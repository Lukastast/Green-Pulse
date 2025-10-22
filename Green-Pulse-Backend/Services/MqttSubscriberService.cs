using MQTTnet;
using MQTTnet.Client;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;

namespace Green_Pulse_Backend.Services
{
    public class MqttSubscriberService : IHostedService
    {
        private readonly ILogger<MqttSubscriberService> _logger;
        private IMqttClient _mqttClient;

        public MqttSubscriberService(ILogger<MqttSubscriberService> logger)
        {
            _logger = logger;
        }

        public async Task StartAsync(CancellationToken cancellationToken)
        {
            _logger.LogInformation("Starting MQTT Subscriber Service...");

            var factory = new MqttFactory();
            _mqttClient = factory.CreateMqttClient();

            // Event for received messages
            _mqttClient.ApplicationMessageReceivedAsync += async e =>
            {
                string topic = e.ApplicationMessage.Topic;
                string payload = Encoding.UTF8.GetString(e.ApplicationMessage.Payload);
                _logger.LogInformation($"Received on {topic}: {payload}");
            };

            // Event for connected
            _mqttClient.ConnectedAsync += async e =>
            {
                _logger.LogInformation("Connected to MQTT broker!");

                // Subscribe to topic
                var subscribeOptions = new MqttClientSubscribeOptionsBuilder()
                    .WithTopicFilter("greenpulse/#")
                    .Build();

                await _mqttClient.SubscribeAsync(subscribeOptions, cancellationToken);
                _logger.LogInformation("Subscribed to greenpulse/#");
            };

            var options = new MqttClientOptionsBuilder()
                .WithClientId("aspnetcore_subscriber")
                .WithTcpServer("broker.emqx.io", 8883)
                .WithTls() // Use TLS
                .Build();

            await _mqttClient.ConnectAsync(options, cancellationToken);
        }

        public async Task StopAsync(CancellationToken cancellationToken)
        {
            _logger.LogInformation("Stopping MQTT Subscriber Service...");
            if (_mqttClient != null)
            {
                await _mqttClient.DisconnectAsync();
            }
        }
    }
}
