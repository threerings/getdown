/*
 * ProxyCredentials.java
 *
 * Copyright (c) 2018 Administration Intelligence AG,
 * Steinbachtal 2b, 97082 Wuerzburg, Germany.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information
 * of Administration Intelligence AG ("Confidential Information").
 * You shall not disclose such Confidential Information and shall use
 * it only in accordance with the terms of the license agreement you
 * entered into with Administration Intelligence.
 */

package com.threerings.getdown.spi.proxyauthentification;

/**
 *
 *
 * @author tgehrig
 */
public class ProxyCredentials
{
  private String user;

  private String password;

  // -------------------------------------------------------------------

  public ProxyCredentials(String aUser, String aPassword)
  {
    user = aUser;
    password = aPassword;
  }

  // -------------------------------------------------------------------

  public String getUser()
  {
    return user;
  }


  // -------------------------------------------------------------------

  public String getPassword()
  {
    return password;
  }


}
