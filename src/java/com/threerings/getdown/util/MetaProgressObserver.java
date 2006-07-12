//
// $Id$
//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2006 Three Rings Design, Inc.
//
// This program is free software; you can redistribute it and/or modify it
// under the terms of the GNU General Public License as published by the Free
// Software Foundation; either version 2 of the License, or (at your option)
// any later version.
//
// This program is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
// more details.
//
// You should have received a copy of the GNU General Public License along with
// this program; if not, write to the: Free Software Foundation, Inc.,
// 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA


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
        _currentSize += elementSize;
        _elementSize = elementSize;
    }

    // documentation inherited from interface
    public void progress (int percent)
    {
        if (_target != null  && _elementSize > 0) {
            long position = _currentSize + (100 * percent / _elementSize);
            _target.progress((int)(100 * position / _totalSize));
        }
    }

    protected ProgressObserver _target;
    protected long _totalSize, _currentSize, _elementSize;
}
