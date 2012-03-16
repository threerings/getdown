//
// $Id$
//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2010 Three Rings Design, Inc.
// http://code.google.com/p/getdown/
//
// Redistribution and use in source and binary forms, with or without modification, are permitted
// provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice, this list of
//    conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice, this list of
//    conditions and the following disclaimer in the documentation and/or other materials provided
//    with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES,
// INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
// PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT,
// INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
// TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
// LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package com.threerings.getdown.util;

/**
 * Accumulates the progress from a number of elements into a single
 * smoothly progressing progress.
 */
public class MetaProgressObserver implements ProgressObserver
{
    public MetaProgressObserver (ProgressObserver target, long totalSize)
    {
        _target = target;
        _totalSize = totalSize;
    }

    public void startElement (long elementSize)
    {
        // add the previous size
        _accum += (_elementSize * 100);
        // then set the new one
        _elementSize = elementSize;
    }

    // documentation inherited from interface
    public void progress (int percent)
    {
        if (_totalSize > 0) {
            _target.progress((int)((_accum + (percent * _elementSize)) / _totalSize));
        }
    }

    protected ProgressObserver _target;
    protected long _totalSize, _accum, _elementSize;
}
