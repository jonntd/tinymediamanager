package org.tinymediamanager.thirdparty;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;
import org.tinymediamanager.core.Utils;

// copy of impl to quick test em

public class ITKodiRPCTestLocal {
  private static final String  SEPARATOR_REGEX  = "[\\/\\\\]+";
  private Map<String, String>  videodatasources = new LinkedHashMap<>(); // dir, label
  // KODI ds|file=id
  private Map<String, Integer> kodiDsAndFolder  = new HashMap<>();
  private Map<String, String>  fileMap          = new HashMap<>();
  private int                  cnt              = 0;

  @Test
  public void testMapping() throws UnsupportedEncodingException {
    loadDatasource("multipath://smb%3a%2f%2f10.0.0.2%2fi%24%2fDownloads%2f/smb%3a%2f%2f10.0.0.2%2fj%24%2fDownloads%2f/");
    loadDatasource("smb://DS1821NAS/Movies/");
    loadDatasource("smb://DS1821NAS/Dupes/");
    getAndSetKodiNames("smb://DS1821NAS/Movies/Action - Adventure/12 Strong (2018)/12 Strong (2018) - 1080p - Blu-ray - DTS.mkv");
    getAndSetKodiNames("smb://DS1821NAS/Dupes/Action - Adventure/12 Strong (2018)/12 Strong (2018) - 1080p - Blu-ray - DTS.mkv");
    getAndSetTmmNames("M:\\", "M:\\Action - Adventure\\12 Strong (2018)\\12 Strong (2018) - 1080p - Blu-ray - DTS.mkv");
    getAndSetTmmNames("D:\\", "D:\\Action - Adventure\\12 Strong (2018)\\12 Strong (2018) - 1080p - Blu-ray - DTS.mkv");

    System.out.println("fin");
  }

  private void loadDatasource(String file) throws UnsupportedEncodingException {
    System.out.println("Kodi datasource: " + file);
    if (file.startsWith("multipath")) {
      // more than one source mapped to a single Kodi datasource
      // multipath://%2fmedia%2f8TB%2fFilme%2fKino%2f/%2fmedia%2fWD-4TB%2f!Kino2%2f/
      String mp = file.replace("multipath://", ""); // remove prefix
      String[] source = mp.split("/"); // split on slash
      for (String ds : source) {
        String s = URLDecoder.decode(ds, "UTF-8");
        System.out.println("DS: " + s);
        this.videodatasources.put(s, "ds name");
      }
    }
    else {
      System.out.println("DS: " + file);
      this.videodatasources.put(file, "ds name");
    }
  }

  private void getAndSetKodiNames(String file) {
    cnt++;
    // stacking only supported on movies
    if (file.startsWith("stack")) {
      String[] files = file.split(" , ");
      for (String s : files) {
        s = s.replaceFirst("^stack://", "");
        String ds = detectDatasource(s);
        String rel = s.replace(ds, ""); // remove ds, to have a relative folder
        rel = rel.replaceAll(SEPARATOR_REGEX, "/"); // normalize separators
        ds = ds.replaceAll(SEPARATOR_REGEX + "$", ""); // replace ending separator
        ds = ds.replaceAll(".*" + SEPARATOR_REGEX, ""); // replace everything till last separator
        if (!kodiDsAndFolder.containsKey(rel)) {
          kodiDsAndFolder.put(rel, cnt);
        }
        else {
          // no putIfAbsent since i wanna have a log!
          System.out.println("Kodi file" + file + " already attached to another datasource - skipping");
        }
      }
    }
    else {
      // Kodi return full path of video file
      String ds = detectDatasource(file); // detect datasource of show dir
      String rel = file.replace(ds, ""); // remove ds, to have a relative folder
      rel = rel.replaceAll(SEPARATOR_REGEX, "/"); // normalize separators
      ds = ds.replaceAll(SEPARATOR_REGEX + "$", ""); // replace ending separator
      ds = ds.replaceAll(".*" + SEPARATOR_REGEX, ""); // replace everything till last separator
      if (!kodiDsAndFolder.containsKey(rel)) {
        kodiDsAndFolder.put(rel, cnt);
      }
      else {
        // no putIfAbsent since i wanna have a log!
        System.out.println("Kodi file" + file + " already attached to another datasource - skipping");
      }
    }
  }

  private void getAndSetTmmNames(String ds, String file) {
    String dsName = parseDatasourceName(Path.of(ds));
    String rel = Utils.relPath(dsName, file); // file relative from datasource
    rel = rel.replaceAll(SEPARATOR_REGEX, "/"); // normalize separators
    if (!fileMap.containsKey(rel)) {
      fileMap.put(rel, "uuid-uuid-uuid");
    }
    else {
      // no putIfAbsent since i wanna have a log!
      System.out.println("Movie dir " + file + " already attached to another datasource - skipping");
    }
  }

  private String detectDatasource(String file) {
    for (String ds : this.videodatasources.keySet()) {
      if (file.startsWith(ds)) {
        return ds;
      }
    }
    return "";
  }

  private String parseDatasourceName(Path ds) {
    // get the name of the datasource folder
    // unfortunately, for UNC paths like \\server\share i cannot get the share name from Path
    // and URI is so slow
    String dsName = "";
    if (ds.getFileName() != null) {
      dsName = ds.getFileName().toString();
    }
    else {
      // try with good old file, which is not so bitchy
      File f = ds.toFile();
      dsName = f.getName();
    }
    if (dsName.isEmpty()) {
      // happens when only a drive letter like M:\ is set - return 1:1
      dsName = ds.toString();
    }
    return dsName;
  }
}
