package org.tinymediamanager.scraper.thesportsdb.entities;

import java.util.Locale;

public class LeagueDetail extends League {
  public String idSoccerXML;
  public String idAPIfootball;
  public String intDivision;
  public String idCup;
  public String strCurrentSeason;
  public String intFormedYear;
  public String dateFirstEvent;
  public String strGender;
  public String strCountry;
  public String strWebsite;
  public String strFacebook;
  public String strInstagram;
  public String strTwitter;
  public String strYoutube;
  public String strRSS;
  public String strDescriptionEN;
  public String strDescriptionDE;
  public String strDescriptionFR;
  public String strDescriptionIT;
  public String strDescriptionCN;
  public String strDescriptionJP;
  public String strDescriptionRU;
  public String strDescriptionES;
  public String strDescriptionPT;
  public String strDescriptionSE;
  public String strDescriptionNL;
  public String strDescriptionHU;
  public String strDescriptionNO;
  public String strDescriptionPL;
  public String strDescriptionIL;
  public String strTvRights;
  public String strFanart1;
  public String strFanart2;
  public String strFanart3;
  public String strFanart4;
  public String strBanner;
  public String strBadge;
  public String strLogo;
  public String strPoster;
  public String strTrophy;
  public String strNaming;
  public String strComplete;
  public String strLocked;

  /**
   * gets the description for language code - might be NULL<br>
   * In that case, try with EN, as it should be always filled....
   * 
   * @param languageCode
   *          in uppercase, like EN, DE, FR, ...
   * @return translated description or NULL
   */
  public String getDescriptionForLanguage(String languageCode) {
    // damn API design
    String desc = switch (languageCode.toUpperCase(Locale.ROOT)) {
      case "EN" -> strDescriptionEN;
      case "DE" -> strDescriptionDE;
      case "FR" -> strDescriptionFR;
      case "IT" -> strDescriptionIT;
      case "CN" -> strDescriptionCN;
      case "JP" -> strDescriptionJP;
      case "RU" -> strDescriptionRU;
      case "ES" -> strDescriptionES;
      case "NL" -> strDescriptionNL;
      case "HU" -> strDescriptionHU;
      case "NO" -> strDescriptionNO;
      case "PL" -> strDescriptionPL;
      case "IL" -> strDescriptionIL;
      default -> strDescriptionEN;
    };

    return desc;
  }
}
