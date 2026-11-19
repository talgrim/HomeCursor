using System.Text;
using System.Threading.Channels;
using Microsoft.Extensions.Logging;

namespace HomeCursor.Core.Diagnostics;

/// <summary>
/// Writes log lines to a size-capped set of files (<c>homecursor.log</c>, <c>homecursor.1.log</c>, ...). Lines are queued
/// and written by a background thread, so logging never waits for the disk. I/O errors drop lines and never throw.
/// </summary>
public sealed class RollingFileLoggerProvider : ILoggerProvider
{
    private const string BaseName = "homecursor";
    private const string Extension = ".log";

    private readonly string _directory;
    private readonly long _maxFileBytes;
    private readonly int _maxFiles;
    private readonly Channel<string> _queue = Channel.CreateUnbounded<string>(new UnboundedChannelOptions { SingleReader = true });
    private readonly Task _writer;
    private StreamWriter? _stream;
    private long _size;

    public RollingFileLoggerProvider(string directory, long maxFileBytes = 5 * 1024 * 1024, int maxFiles = 3)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(directory);
        ArgumentOutOfRangeException.ThrowIfLessThan(maxFileBytes, 1);
        ArgumentOutOfRangeException.ThrowIfLessThan(maxFiles, 1);

        _directory = directory;
        _maxFileBytes = maxFileBytes;
        _maxFiles = maxFiles;
        _writer = Task.Run(WriteLoopAsync);
    }

    public ILogger CreateLogger(string categoryName) => new FileLogger(categoryName, this);

    public void Dispose()
    {
        _queue.Writer.TryComplete();
        _writer.Wait(TimeSpan.FromSeconds(2));
    }

    private string PathOf(int index) =>
        Path.Combine(_directory, index == 0 ? BaseName + Extension : $"{BaseName}.{index}{Extension}");

    private async Task WriteLoopAsync()
    {
        try
        {
            await foreach (var line in _queue.Reader.ReadAllAsync().ConfigureAwait(false))
            {
                WriteLine(line);
            }
        }
        finally
        {
            CloseStream();
        }
    }

    private void WriteLine(string line)
    {
        try
        {
            var bytes = Encoding.UTF8.GetByteCount(line) + Environment.NewLine.Length;
            if (_stream is null)
            {
                Open();
            }

            if (_size > 0 && _size + bytes > _maxFileBytes)
            {
                Rotate();
            }

            _stream!.WriteLine(line);
            _stream.Flush();
            _size += bytes;
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            // Diagnostics must never take the host down; drop the line and retry the file on the next one.
            CloseStream();
        }
    }

    private void Open()
    {
        Directory.CreateDirectory(_directory);
        var stream = new FileStream(PathOf(0), FileMode.Append, FileAccess.Write, FileShare.Read);
        _size = stream.Length;
        _stream = new StreamWriter(stream, new UTF8Encoding(false));
    }

    private void Rotate()
    {
        CloseStream();

        File.Delete(PathOf(_maxFiles - 1));
        for (var index = _maxFiles - 2; index >= 0; index--)
        {
            if (File.Exists(PathOf(index)))
            {
                File.Move(PathOf(index), PathOf(index + 1), overwrite: true);
            }
        }

        Open();
    }

    private void CloseStream()
    {
        try
        {
            _stream?.Dispose();
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            // Nothing more to do with a file that cannot be closed.
        }

        _stream = null;
        _size = 0;
    }

    private void Enqueue(string line) => _queue.Writer.TryWrite(line);

    private static string ShortLevel(LogLevel level) => level switch
    {
        LogLevel.Trace => "TRC",
        LogLevel.Debug => "DBG",
        LogLevel.Information => "INF",
        LogLevel.Warning => "WRN",
        LogLevel.Error => "ERR",
        LogLevel.Critical => "CRT",
        _ => "???"
    };

    private sealed class FileLogger(string category, RollingFileLoggerProvider provider) : ILogger
    {
        public IDisposable? BeginScope<TState>(TState state)
            where TState : notnull => null;

        public bool IsEnabled(LogLevel logLevel) => logLevel != LogLevel.None;

        public void Log<TState>(
            LogLevel logLevel, EventId eventId, TState state, Exception? exception, Func<TState, Exception?, string> formatter)
        {
            if (!IsEnabled(logLevel))
            {
                return;
            }

            var line = $"{DateTimeOffset.Now:yyyy-MM-dd HH:mm:ss.fff} [{ShortLevel(logLevel)}] {category}: {formatter(state, exception)}";
            if (exception is not null)
            {
                line += Environment.NewLine + exception;
            }

            provider.Enqueue(line);
        }
    }
}
