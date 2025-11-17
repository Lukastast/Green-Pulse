using Microsoft.AspNetCore.Mvc;

namespace Green_Pulse_Backend.Controllers
{
    [ApiController]
    [Route("api/watering")]
    public class WateringController : ControllerBase
    {
        /* private readonly WateringService _wateringService;

        public WateringController(WateringService wateringService)
        {
            _wateringService = wateringService;
        }

        [HttpGet("humidityStatuses")]
        public IActionResult GetHumidityStatuses()
        {
            return Ok(_wateringService.GetStatuses());
        }

        [HttpGet("plantsToWater")]
        public IActionResult GetPlantsToWater()
        {
            var plantsToWater = _wateringService.GetStatuses()
                .Where(p => p.NeedsWater)
                .ToList();

            return Ok(plantsToWater);
        } */

    }
}
