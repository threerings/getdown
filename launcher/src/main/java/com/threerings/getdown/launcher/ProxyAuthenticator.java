/*
 * ProxyAuthentification.java
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

package com.threerings.getdown.launcher;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

/**
 *
 *
 * @author tgehrig
 */
public class ProxyAuthenticator extends Authenticator
{

  private String userName;
  private char[] password;

  @Override
  protected PasswordAuthentication getPasswordAuthentication()
  {
    return new PasswordAuthentication(userName, password);
  }

  public ProxyAuthenticator(String userName, char[] password)
  {
    this.userName = userName;
    this.password = password;
  }

}
