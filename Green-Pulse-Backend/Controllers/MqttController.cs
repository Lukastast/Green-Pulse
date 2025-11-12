using Microsoft.AspNetCore.Mvc;

namespace Green_Pulse_Backend.Controllers
{
    [ApiController]
    [Route("api/mqtt")]
    public class MqttController : ControllerBase
    {
        private readonly Services.MqttSubscriberService _mqttService;

        public MqttController(Services.MqttSubscriberService mqttService)
        {
            _mqttService = mqttService;
        }

        [HttpGet("subsribeToMqtt")]
        public IActionResult GetMessages()
        {
            return Ok(_mqttService.Messages);
        }
    }
}