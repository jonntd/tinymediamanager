package org.tinymediamanager.scraper.thesportsdb;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;
import org.tinymediamanager.scraper.thesportsdb.entities.Events;

import retrofit2.Response;

public class ITTsdbTest {

  @Test
  public void sumLeagues() {
    // our loaded leagues
    // get em from https://www.thesportsdb.com/api/v1/json/xxx/all_leagues.php (use paid key)
    Assert.assertTrue(TheSportsDbHelper.SPORT_LEAGUES.size() > 1000);
  }

  @Test
  public void sumEventsFree3() throws IOException {
    TheSportsDbController api = new TheSportsDbController("3");
    Response<Events> response = api.ScheduleServiceV1().getEvents("4445", "2021").execute();
    Events events = response.body();
    Assert.assertNotNull(events);
    Assert.assertNotNull(events.events);
    Assert.assertEquals(events.events.size(), 100); // only 100 with the OLD free key "3"
  }

  @Test
  public void sumEventsFree123() throws IOException {
    TheSportsDbController api = new TheSportsDbController("123");
    Response<Events> response = api.ScheduleServiceV1().getEvents("4445", "2021").execute();
    Events events = response.body();
    Assert.assertNotNull(events);
    Assert.assertNotNull(events.events);
    Assert.assertEquals(events.events.size(), 15); // but just 15 with NEW free key "123"
  }

}
