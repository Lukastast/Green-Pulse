using System.Text.Json;
using MQTTnet;
using MQTTnet.Client;
using Microsoft.Extensions.Logging;

namespace Green_Pulse_Backend.Services
{
    public class PlantControlService
    {
        private readonly ILogger<PlantControlService> _logger;
        private readonly IMqttClient _mqttClient;

        public PlantControlService(ILogger<PlantControlService> logger)
        {
            _logger = logger;
            var factory = new MqttFactory();
            _mqttClient = factory.CreateMqttClient();
        }

        public async Task InitializeAsync(CancellationToken cancellationToken = default)
        {
            var options = new MqttClientOptionsBuilder()
                .WithClientId("plant_control_service")
                .WithTcpServer("localhost", 1883) // eller "broker.emqx.io" hvis du bruger ekstern broker
                .Build();

            _mqttClient.ConnectedAsync += async e =>
            {
                _logger.LogInformation("✅ PlantControlService connected to MQTT broker");
            };

            await _mqttClient.ConnectAsync(options, cancellationToken);
        }

        public async Task WaterPlantAsync(string plantId, double amount = 0.3)
        {
            if (!_mqttClient.IsConnected)
            {
                _logger.LogWarning("⚠️ MQTT client not connected. Cannot send watering command.");
                return;
            }

            var payload = new
            {
                action = "water",
                plantId,
                amount
            };

            var message = new MqttApplicationMessageBuilder()
                .WithTopic("greenpulse/simulator")
                .WithPayload(JsonSerializer.Serialize(payload))
                .WithQualityOfServiceLevel(MQTTnet.Protocol.MqttQualityOfServiceLevel.ExactlyOnce)
                .Build();

            await _mqttClient.PublishAsync(message);
            _logger.LogInformation($"💧 Sent watering command for {plantId} (amount: {amount})");
        }

    }
}
