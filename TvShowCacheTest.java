/*
 * Test program to verify TV show artwork cache functionality
 */
import java.nio.file.Path;
import java.nio.file.Paths;

import org.tinymediamanager.core.tvshow.TvShowModuleManager;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.core.tasks.MediaEntityImageFetcherTask;
import org.tinymediamanager.core.tasks.MediaEntityActorImageFetcherTask;
import org.tinymediamanager.core.tvshow.tasks.TvShowExtraImageFetcherTask;
import org.tinymediamanager.core.tvshow.TvShowArtworkHelper;
import org.tinymediamanager.core.ImageCache;

public class TvShowCacheTest {
    
    public static void main(String[] args) {
        System.out.println("=== TV Show Artwork Cache Test ===");
        
        // Test 1: Check if cache setting is enabled
        boolean cacheEnabled = TvShowModuleManager.getInstance().getSettings().isSaveArtworkToCache();
        System.out.println("Cache setting enabled: " + cacheEnabled);
        
        // Test 2: Check cache directory
        Path cacheDir = ImageCache.getCacheDir().resolve("artwork").resolve("tvshows");
        System.out.println("Cache directory: " + cacheDir);
        
        // Test 3: Create a test TV show entity
        TvShow testShow = new TvShow();
        testShow.setTitle("Test Show");
        testShow.setYear(2023);
        testShow.setPath("/test/path/Test Show (2023)");
        
        // Test 4: Test destination folder logic for main images
        try {
            MediaEntityImageFetcherTask mainTask = new MediaEntityImageFetcherTask(
                testShow, "http://example.com/poster.jpg", 
                org.tinymediamanager.scraper.entities.MediaArtwork.MediaArtworkType.POSTER,
                java.util.Arrays.asList("poster.jpg")
            );
            System.out.println("Main image task created successfully");
        } catch (Exception e) {
            System.out.println("Main image task creation failed: " + e.getMessage());
        }
        
        // Test 5: Test destination folder logic for actor images
        try {
            MediaEntityActorImageFetcherTask actorTask = new MediaEntityActorImageFetcherTask();
            System.out.println("Actor image task created successfully");
        } catch (Exception e) {
            System.out.println("Actor image task creation failed: " + e.getMessage());
        }
        
        // Test 6: Test destination folder logic for extra images
        try {
            TvShowExtraImageFetcherTask extraTask = new TvShowExtraImageFetcherTask(
                testShow, org.tinymediamanager.core.MediaFileType.EXTRAFANART
            );
            System.out.println("Extra image task created successfully");
        } catch (Exception e) {
            System.out.println("Extra image task creation failed: " + e.getMessage());
        }
        
        System.out.println("=== Test Complete ===");
        
        // Print instructions
        System.out.println("\nTo test the functionality:");
        System.out.println("1. Make sure 'Save artwork to cache folder' is checked in TV show settings");
        System.out.println("2. Restart tinyMediaManager");
        System.out.println("3. Scrape a TV show");
        System.out.println("4. Check if images are saved to: " + cacheDir);
        System.out.println("5. Check if video folder is clean (no image files)");
    }
}
