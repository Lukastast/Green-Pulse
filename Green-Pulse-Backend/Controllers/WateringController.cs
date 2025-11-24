using Microsoft.AspNetCore.Mvc;
using GreenPulse.Services;

namespace GreenPulse.Controllers
{
    [ApiController]
    [Route("api/watering")]
    public class WateringController : ControllerBase
    {
        private readonly WateringService _wateringService;
        private readonly ILogger<WateringController> _logger;

        public WateringController(
            WateringService wateringService,
            ILogger<WateringController> logger)
        {
            _wateringService = wateringService;
            _logger = logger;
        }

        /// <summary>
        /// Get humidity status for all plants
        /// </summary>
        [HttpGet("humidityStatuses")]
        public IActionResult GetHumidityStatuses()
        {
            try
            {
                var statuses = _wateringService.GetStatuses();
                return Ok(new
                {
                    totalPlants = statuses.Count,
                    plants = statuses
                });
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting humidity statuses");
                return StatusCode(500, "Error retrieving humidity statuses");
            }
        }

        /// <summary>
        /// Get list of plants that need watering
        /// </summary>
        [HttpGet("plantsToWater")]
        public IActionResult GetPlantsToWater()
        {
            try
            {
                var plantsToWater = _wateringService.GetStatuses()
                    .Where(p => p.NeedsWater)
                    .OrderBy(p => p.Humidity) // Most dry first
                    .ToList();

                return Ok(new
                {
                    count = plantsToWater.Count,
                    plants = plantsToWater
                });
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting plants to water");
                return StatusCode(500, "Error retrieving plants to water");
            }
        }

        /// <summary>
        /// Get status for a specific plant
        /// </summary>
        [HttpGet("plant/{plantId}")]
        public IActionResult GetPlantStatus(string plantId)
        {
            try
            {
                var plant = _wateringService.GetStatuses()
                    .FirstOrDefault(p => p.PlantId == plantId);

                if (plant == null)
                {
                    return NotFound(new { message = $"Plant {plantId} not found" });
                }

                return Ok(plant);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting plant status for {PlantId}", plantId);
                return StatusCode(500, "Error retrieving plant status");
            }
        }

        /// <summary>
        /// Get summary statistics
        /// </summary>
        [HttpGet("summary")]
        public IActionResult GetSummary()
        {
            try
            {
                var statuses = _wateringService.GetStatuses();
                
                return Ok(new
                {
                    totalPlants = statuses.Count,
                    plantsNeedingWater = statuses.Count(p => p.NeedsWater),
                    averageHumidity = statuses.Any() ? statuses.Average(p => p.Humidity) : 0,
                    lowestHumidity = statuses.Any() ? statuses.Min(p => p.Humidity) : 0,
                    highestHumidity = statuses.Any() ? statuses.Max(p => p.Humidity) : 0
                });
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting watering summary");
                return StatusCode(500, "Error retrieving summary");
            }
        }
    }
}