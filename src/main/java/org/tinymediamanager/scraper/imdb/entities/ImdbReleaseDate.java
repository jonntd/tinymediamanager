package org.tinymediamanager.scraper.imdb.entities;

import java.util.Date;
import java.util.GregorianCalendar;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class ImdbReleaseDate extends BaseJsonEntity {
  public int         day;
  public int         month;
  public int         year;
  public ImdbCountry country; // on reference page

  /**
   * @return Date or NULL
   */
  public Date toDate() {
    try {
      Date date = new GregorianCalendar(year, month - 1, day).getTime();
      return date;
    }
    catch (Exception e) {
      return null;
    }
  }
}
