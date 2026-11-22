using System.Text;
using System.Text.Json;

namespace HomeCursor.Core.Settings;

/// <summary>
/// Changes top-level values of a JSON object in place, as text, so comments, formatting and keys it does not
/// know about survive. <see cref="JsonSerializer"/> and the DOM types drop comments, so they cannot do this.
/// </summary>
internal static class JsonObjectPatcher
{
    private const string Indent = "  ";

    /// <summary>
    /// Sets top-level properties of the JSON object <paramref name="json"/>.
    /// </summary>
    /// <param name="json">A JSON object; comments and trailing commas are allowed.</param>
    /// <param name="values">Property name to its new value as JSON text. Names match ignoring case, like the settings reader.</param>
    /// <param name="defaults">
    /// Property name to the JSON text of its default. A value from <paramref name="values"/> that is missing from the
    /// object and equals its default is not added.
    /// </param>
    /// <exception cref="JsonException"><paramref name="json"/> is not a valid JSON object.</exception>
    public static string Patch(
        string json, IReadOnlyDictionary<string, string> values, IReadOnlyDictionary<string, string> defaults)
    {
        var bytes = Encoding.UTF8.GetBytes(json);
        var replacements = new List<Replacement>();
        var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var lookup = new Dictionary<string, string>(values, StringComparer.OrdinalIgnoreCase);

        var reader = new Utf8JsonReader(
            bytes, new JsonReaderOptions { CommentHandling = JsonCommentHandling.Allow, AllowTrailingCommas = true });

        if (!reader.Read() || reader.TokenType != JsonTokenType.StartObject)
        {
            throw new JsonException("The settings file does not contain a JSON object.");
        }

        var insertAt = (int)reader.TokenStartIndex + 1;
        var hasProperties = false;

        while (reader.Read() && reader.TokenType != JsonTokenType.EndObject)
        {
            if (reader.TokenType == JsonTokenType.Comment)
            {
                continue;
            }

            var name = reader.GetString()!;
            if (!ReadValue(ref reader, out var start, out var end))
            {
                throw new JsonException("The settings file ends in the middle of a property.");
            }

            hasProperties = true;
            insertAt = end;
            seen.Add(name);

            if (lookup.TryGetValue(name, out var newValue) &&
                Encoding.UTF8.GetString(bytes, start, end - start) != newValue)
            {
                replacements.Add(new Replacement(start, end, newValue));
            }
        }

        var closeAt = (int)reader.TokenStartIndex;
        var added = new StringBuilder();
        var newLine = json.Contains("\r\n", StringComparison.Ordinal) ? "\r\n" : "\n";
        foreach (var (name, value) in values)
        {
            if (seen.Contains(name) || (defaults.TryGetValue(name, out var defaultValue) && defaultValue == value))
            {
                continue;
            }

            if (hasProperties || added.Length > 0)
            {
                added.Append(',');
            }

            added.Append(newLine).Append(Indent).Append(JsonSerializer.Serialize(name)).Append(": ").Append(value);
        }

        if (added.Length > 0)
        {
            if (!hasProperties && !bytes.AsSpan(insertAt, closeAt - insertAt).Contains((byte)'\n'))
            {
                // Keep the closing brace on its own line when the object was written as {}.
                added.Append(newLine);
            }

            replacements.Add(new Replacement(insertAt, insertAt, added.ToString()));
        }

        // Apply from the end so earlier offsets stay valid.
        var result = new List<byte>(bytes);
        foreach (var replacement in replacements.OrderByDescending(r => r.Start))
        {
            result.RemoveRange(replacement.Start, replacement.End - replacement.Start);
            result.InsertRange(replacement.Start, Encoding.UTF8.GetBytes(replacement.Text));
        }

        return Encoding.UTF8.GetString([.. result]);
    }

    /// <summary>Reads the value after a property name, skipping comments, and reports its byte range.</summary>
    private static bool ReadValue(ref Utf8JsonReader reader, out int start, out int end)
    {
        start = end = 0;
        while (reader.Read())
        {
            if (reader.TokenType == JsonTokenType.Comment)
            {
                continue;
            }

            start = (int)reader.TokenStartIndex;
            switch (reader.TokenType)
            {
                case JsonTokenType.StartObject or JsonTokenType.StartArray:
                    reader.Skip();
                    end = (int)reader.TokenStartIndex + 1;
                    break;
                case JsonTokenType.String:
                    // ValueSpan is the raw text between the quotes.
                    end = start + (reader.HasValueSequence ? (int)reader.ValueSequence.Length : reader.ValueSpan.Length) + 2;
                    break;
                default:
                    end = start + (reader.HasValueSequence ? (int)reader.ValueSequence.Length : reader.ValueSpan.Length);
                    break;
            }

            return true;
        }

        return false;
    }

    private readonly record struct Replacement(int Start, int End, string Text);
}
