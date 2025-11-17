using Microsoft.AspNetCore.Mvc;

namespace Green_Pulse_Backend.Controllers
{
    [ApiController]
    [Route("api/plants")]
    public class PlantsController : ControllerBase
    {
       /*  private readonly PlantControlService _plantControlService;
        private readonly WateringService _wateringService;

        public PlantsController(PlantControlService plantControlService, WateringService wateringService)
        {
            _plantControlService = plantControlService;
            _wateringService = wateringService;
        }

        [HttpPost("{plantId}/waterPlant")]
        public async Task<IActionResult> WaterPlant(string plantId, [FromQuery] double amount = 0.3)
        {
            await _plantControlService.WaterPlantAsync(plantId, amount);
            return Ok(new { message = $"Water command sent for {plantId}" });
        } */
    }
}
