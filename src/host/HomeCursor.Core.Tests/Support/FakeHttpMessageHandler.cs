using System.Net;

namespace HomeCursor.Core.Tests.Support;

internal sealed class FakeHttpMessageHandler : HttpMessageHandler
{
    private readonly Func<HttpRequestMessage, HttpResponseMessage> _respond;

    public FakeHttpMessageHandler(Func<HttpRequestMessage, HttpResponseMessage> respond) => _respond = respond;

    public List<Uri> Requests { get; } = [];

    public static FakeHttpMessageHandler Serving(string content) =>
        new(_ => new HttpResponseMessage(HttpStatusCode.OK) { Content = new StringContent(content) });

    public static FakeHttpMessageHandler Serving(byte[] content) =>
        new(_ => new HttpResponseMessage(HttpStatusCode.OK) { Content = new ByteArrayContent(content) });

    public static FakeHttpMessageHandler Failing(HttpStatusCode status) =>
        new(_ => new HttpResponseMessage(status));

    protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
    {
        Requests.Add(request.RequestUri!);
        return Task.FromResult(_respond(request));
    }
}
