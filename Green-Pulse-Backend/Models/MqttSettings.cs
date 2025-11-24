// Models/MqttSettings.cs
namespace Green_Pulse_Backend.Models;

public class MqttSettings
{
    public string Broker { get; set; } = string.Empty;
    public int Port { get; set; }
    public string Username { get; set; } = string.Empty;
    public string Password { get; set; } = string.Empty;
    public string Topic { get; set; } = string.Empty;
}