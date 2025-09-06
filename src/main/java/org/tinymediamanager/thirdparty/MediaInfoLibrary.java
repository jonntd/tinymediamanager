/*
 * Copyright 2012 - 2025 Manuel Laggner
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
package org.tinymediamanager.thirdparty;

import static java.util.Collections.singletonMap;

import com.sun.jna.FunctionMapper;
import com.sun.jna.IntegerType;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.WString;

/**
 * The Interface MediaInfoLibrary.
 * 
 * @author Myron Boyle
 */
interface MediaInfoLibrary extends Library {

  class SizeT extends IntegerType {
    public SizeT() {
      super(Native.SIZE_T_SIZE, 0, true);
    }

    public SizeT(long value) {
      super(Native.SIZE_T_SIZE, value, true);
    }
  }

  // libmediainfo for linux depends on libzen, so we need to load dependencies first, because we know where our native libs are (e.g. Java Web Start
  // Cache).
  // if we do not, the system will look for dependencies, but only in the library path
  Library          LIB_ZEN  = Platform.isLinux() ? Native.load("zen", Library.class) : null;

  MediaInfoLibrary INSTANCE = Native.load("mediainfo", MediaInfoLibrary.class,
      singletonMap(OPTION_FUNCTION_MAPPER, (FunctionMapper) (lib, method) -> {
                                  // MediaInfo_New(), MediaInfo_Open() ...
                                  return "MediaInfo_" + method.getName();
                                }));

  //@formatter:off
  
  //Constructor/Destructor
  Pointer New();
  void Delete(Pointer Handle);

  //File
  SizeT Open(Pointer Handle, WString file);
  SizeT Open_Buffer_Init(Pointer handle, long length, long offset);
  SizeT Open_Buffer_Continue(Pointer handle, byte[] buffer, SizeT size);
  long  Open_Buffer_Continue_GoTo_Get(Pointer handle);
  SizeT Open_Buffer_Finalize(Pointer handle);
  void  Close(Pointer Handle);

  //Infos
  WString Inform(Pointer Handle, int Reserved);
  WString Get(Pointer Handle, int StreamKind, SizeT StreamNumber, WString parameter, int infoKind, int searchKind);
  WString GetI(Pointer Handle, int StreamKind, SizeT StreamNumber, SizeT parameterIndex, int infoKind);
  SizeT   Count_Get(Pointer Handle, int StreamKind, SizeT StreamNumber);

  //Options
  WString Option(Pointer Handle, WString option, WString value);
}
