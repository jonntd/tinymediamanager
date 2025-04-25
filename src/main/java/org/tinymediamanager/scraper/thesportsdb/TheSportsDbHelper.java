package org.tinymediamanager.scraper.thesportsdb;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.scraper.thesportsdb.entities.League;
import org.tinymediamanager.scraper.thesportsdb.entities.Leagues;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

public class TheSportsDbHelper {
  private static final Logger             LOGGER        = LoggerFactory.getLogger(TheSportsDbHelper.class);

  public static final Map<String, League> SPORT_LEAGUES = loadLeagues();

  private synchronized static Map<String, League> loadLeagues() {
    Map<String, League> ret = new HashMap<>();
    try {
      InputStream is = TheSportsDbHelper.class.getResourceAsStream("/org/tinymediamanager/scraper/TSDB_all_leagues.php.json");
      JsonReader reader = new JsonReader(new InputStreamReader(is));
      Leagues loaded = new Gson().fromJson(reader, Leagues.class);
      loaded.leagues.forEach(league -> {
        ret.put(league.strLeague, league);
        if (league.strLeagueAlternate != null) {
          String[] alt = league.strLeagueAlternate.split("\\s*,\\s*"); // trimSplit, stripSplit ;)
          for (String a : alt) {
            if (!a.isEmpty()) {
              ret.put(a, league);
            }
          }
        }
      });
      return ret;
    }
    catch (Exception e) {
      LOGGER.error("Could not load leagues from json file: {}", e);
    }
    return Collections.emptyMap();
  }

  // public static final List<String> SPORT_NAMES = loadSports(); // unneeded so far

  // private synchronized static List<String> loadSports() {
  // List<String> ret = new ArrayList<>();
  // try {
  // InputStream is = TheSportsDbHelper.class.getResourceAsStream("/org/tinymediamanager/scraper/TSDB_all_sports.php.json");
  // JsonReader reader = new JsonReader(new InputStreamReader(is));
  // Sports loaded = new Gson().fromJson(reader, Sports.class);
  // loaded.sports.forEach(sport -> ret.add(sport.strSport));
  // return ret;
  // }
  // catch (Exception e) {
  // e.printStackTrace();
  // }
  // return Collections.emptyList();
  // }
}
