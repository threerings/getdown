//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2016 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.data;

/**
 * System property constants associated with Getdown.
 */
public class Properties
{
    /** This property will be set to "true" on the application when it is being run by getdown. */
    public static final String GETDOWN = "com.threerings.getdown";

    /** If accepting connections from the launched application, this property
     * will be set to the connection server port. */
    public static final String CONNECT_PORT = "com.threerings.getdown.connectPort";
}
