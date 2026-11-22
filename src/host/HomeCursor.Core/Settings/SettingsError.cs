namespace HomeCursor.Core.Settings;

/// <summary>One invalid value: the <see cref="HostSettings"/> property it belongs to and a message for the user.</summary>
public sealed record SettingsError(string Property, string Message);
