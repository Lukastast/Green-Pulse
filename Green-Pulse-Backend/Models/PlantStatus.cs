namespace Green_Pulse_Backend.Models
{
    public class PlantStatus
    {
        public string PlantId { get; set; } = string.Empty;
        public double Humidity { get; set; }
        public bool NeedsWater { get; set; } = false; // hysterese flag
    }
}
