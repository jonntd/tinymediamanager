package org.tinymediamanager.scraper.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.tinymediamanager.core.BasicTest;

public class MetadataUtilTest extends BasicTest {

  @Test
  public void testParseInt() {
    // Standard integer parsing
    assertThat(MetadataUtil.parseInt("2000")).isEqualTo(2000);
    assertThat(MetadataUtil.parseInt("2.000")).isEqualTo(2000);
    assertThat(MetadataUtil.parseInt("2,000")).isEqualTo(2000);
    assertThat(MetadataUtil.parseInt("2 000")).isEqualTo(2000);

    // Scientific notation parsing
    assertThat(MetadataUtil.parseInt("2019E")).isEqualTo(2019);  // The problematic case
    assertThat(MetadataUtil.parseInt("2E3")).isEqualTo(2000);
    assertThat(MetadataUtil.parseInt("1.5E3")).isEqualTo(1500);
    assertThat(MetadataUtil.parseInt("1E6")).isEqualTo(1000000);
    assertThat(MetadataUtil.parseInt("-1.5E3")).isEqualTo(-1500);

    // Edge cases
    assertThat(MetadataUtil.parseInt("0")).isEqualTo(0);
    assertThat(MetadataUtil.parseInt("-123")).isEqualTo(-123);
  }
}
