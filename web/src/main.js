document.addEventListener("DOMContentLoaded", () => {
    const statsElement = document.getElementById('frame-stats');
    if (statsElement) {
        const stats = {
            fps: 15,
            resolution: "1280x720",
            filter: "Canny (80, 100)"
        };
        statsElement.innerText = `Sample Stats: ${stats.resolution} @ ${stats.fps} FPS | Filter: ${stats.filter}`;
    }
    else {
        console.error("Error: Could not find the 'frame-stats' element.");
    }
});
export {};
//# sourceMappingURL=main.js.map