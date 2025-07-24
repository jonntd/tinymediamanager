/*
 * Copyright 2012 - 2020 Manuel Laggner
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tinymediamanager.scraper.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.Test;
import org.tinymediamanager.scraper.entities.MediaArtwork;

public class ListUtilsTest {

  @Test
  public void testNullSafeWithNull() {
    Iterable<Object> result = ListUtils.nullSafe(null);
    assertThat(result).isEmpty();
  }

  @Test
  public void testNullSafeWithNonNull() {
    List<Integer> list = List.of(1, 2, 3);
    Iterable<Integer> result = ListUtils.nullSafe(list);
    assertThat(result).containsExactly(1, 2, 3);
  }

  @Test
  public void testMergeLists() {
    List<Integer> baseList = new ArrayList<>(List.of(1, 3, 5));
    List<Integer> newItems = List.of(2, 3, 4);
    ListUtils.mergeLists(baseList, newItems);
    assertThat(baseList).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
  }

  @Test
  public void testIsEmptyWithNull() {
    assertThat(ListUtils.isEmpty(null)).isTrue();
  }

  @Test
  public void testIsEmptyWithEmptyList() {
    assertThat(ListUtils.isEmpty(new ArrayList<>())).isTrue();
  }

  @Test
  public void testIsEmptyWithNonEmptyList() {
    assertThat(ListUtils.isEmpty(List.of(1))).isFalse();
  }

  @Test
  public void testIsNotEmptyWithNull() {
    assertThat(ListUtils.isNotEmpty(null)).isFalse();
  }

  @Test
  public void testIsNotEmptyWithEmptyList() {
    assertThat(ListUtils.isNotEmpty(new ArrayList<>())).isFalse();
  }

  @Test
  public void testIsNotEmptyWithNonEmptyList() {
    assertThat(ListUtils.isNotEmpty(List.of(1))).isTrue();
  }

  @Test
  public void testAsSortedListNaturalOrder() {
    List<Integer> unsorted = List.of(3, 1, 2);
    List<Integer> sorted = ListUtils.asSortedList(unsorted);
    assertThat(sorted).containsExactly(1, 2, 3);
  }

  @Test
  public void testAsSortedListWithComparator() {
    List<String> unsorted = List.of("b", "a", "c");
    List<String> sorted = ListUtils.asSortedList(unsorted, Comparator.reverseOrder());
    assertThat(sorted).containsExactly("c", "b", "a");
  }

  @Test
  public void testAddToCopyOnWriteArrayListIfAbsent() {
    CopyOnWriteArrayList<Integer> list = new CopyOnWriteArrayList<>(List.of(1, 2));
    boolean result = ListUtils.addToCopyOnWriteArrayListIfAbsent(list, List.of(2, 3, 4));
    assertThat(result).isTrue();
    assertThat(list).containsExactly(1, 2, 3, 4);
  }

  @Test
  public void testContainsAny() {
    List<Integer> list = List.of(1, 2, 3);
    assertThat(ListUtils.containsAny(list, 2, 4)).isTrue();
    assertThat(ListUtils.containsAny(list, 4, 5)).isFalse();
  }

  @Test
  public void testGetFirst() {
    List<Integer> list = List.of(1, 2, 3);
    assertThat(ListUtils.getFirst(list)).isEqualTo(1);
    assertThat(ListUtils.getFirst(new ArrayList<Integer>())).isNull();
    assertThat(ListUtils.getMiddle((List<Integer>) null)).isNull();
  }

  @Test
  public void testGetMiddle() {
    List<Integer> singleList = List.of(1);
    List<Integer> doubleList = List.of(1, 2);
    List<Integer> oddList = List.of(1, 2, 3);
    List<Integer> evenList = List.of(1, 2, 3, 4);
    assertThat(ListUtils.getMiddle(singleList)).isEqualTo(1);
    assertThat(ListUtils.getMiddle(doubleList)).isEqualTo(2);
    assertThat(ListUtils.getMiddle(oddList)).isEqualTo(2);
    assertThat(ListUtils.getMiddle(evenList)).isEqualTo(3);
    assertThat(ListUtils.getMiddle(new ArrayList<Integer>())).isNull();
    assertThat(ListUtils.getMiddle((List<Integer>) null)).isNull();
  }

  @Test
  public void sortSizeOrderByPreference() {
    int preferred = 8;
    List<Integer> sorted = MediaArtwork.sortSizeOrderByPreference(preferred);

    assertThat(sorted).isNotEmpty();
    assertThat(sorted.get(0)).isEqualTo(8);
    assertThat(sorted.get(1)).isEqualTo(16);
    assertThat(sorted.get(2)).isEqualTo(4);
    assertThat(sorted.get(3)).isEqualTo(32);
    assertThat(sorted.get(4)).isEqualTo(2);
    assertThat(sorted.get(5)).isEqualTo(1);

    preferred = 2;
    sorted = MediaArtwork.sortSizeOrderByPreference(preferred);

    assertThat(sorted).isNotEmpty();
    assertThat(sorted.get(0)).isEqualTo(2);
    assertThat(sorted.get(1)).isEqualTo(4);
    assertThat(sorted.get(2)).isEqualTo(1);
    assertThat(sorted.get(3)).isEqualTo(8);
    assertThat(sorted.get(4)).isEqualTo(16);
    assertThat(sorted.get(5)).isEqualTo(32);

    preferred = 8;
    sorted = MediaArtwork.sortSizeOrderByPreference(preferred);

    assertThat(sorted).isNotEmpty();
    assertThat(sorted.get(0)).isEqualTo(8);
    assertThat(sorted.get(1)).isEqualTo(16);
    assertThat(sorted.get(2)).isEqualTo(4);
    assertThat(sorted.get(3)).isEqualTo(32);
    assertThat(sorted.get(4)).isEqualTo(2);
    assertThat(sorted.get(5)).isEqualTo(1);
  }

  @Test
  public void testGetLast() {
    List<Integer> list = List.of(1, 2, 3);
    assertThat(ListUtils.getLast(list)).isEqualTo(3);
    assertThat(ListUtils.getLast(new ArrayList<Integer>())).isNull();
    assertThat(ListUtils.getLast((List<Integer>) null)).isNull();
  }
}
