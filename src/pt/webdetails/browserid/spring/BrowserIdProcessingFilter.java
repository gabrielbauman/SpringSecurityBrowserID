/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.webdetails.browserid.spring;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpException;
import org.springframework.security.Authentication;
import org.springframework.security.AuthenticationException;
import org.springframework.security.ui.AbstractProcessingFilter;
import org.springframework.util.Assert;

import pt.webdetails.browserid.BrowserIdResponse;
import pt.webdetails.browserid.BrowserIdVerifier;

/**
 * Spring security filter for BrowserID authentication.
 */
public class BrowserIdProcessingFilter extends AbstractProcessingFilter {

  private static final String DEFAULT_FILTER_PROCESS_URL = "/j_spring_security_check";
  private static final String DEFAULT_ASSERTION_PARAMETER = "assertion";
  private static Log log = LogFactory.getLog(BrowserIdProcessingFilter.class);
  
  private String verificationServiceUrl; 
  private String assertionParameterName = DEFAULT_ASSERTION_PARAMETER;
  private String hostname;
  private String hostnameInitParameter;
  private boolean checkUID=true;
  private String uidSessionName = "suuid";
  private String uidParamName = uidSessionName;
  private String proxyUrl;

  private int order;
  
  public String getAssertionParameterName() {
    return assertionParameterName;
  }

  /**
   * 
   * @param assertionParameterName 
   */
  public void setAssertionParameterName(String assertionParameterName) {
    this.assertionParameterName = assertionParameterName;
  }


  public String getVerificationServiceUrl() {
    return verificationServiceUrl;
  }

  public void setVerificationServiceUrl(String verificationServiceUrl) {
    this.verificationServiceUrl = verificationServiceUrl;
  }
  
  
  public String getHostname() {
    return hostname;
  }

  public void setHostname(String hostname) {
    this.hostname = hostname;
  }
  
  public String getHostnameInitParameter() {
    return hostnameInitParameter;
  }

  public void setHostnameInitParameter(String hostnameInitParameter) {
    this.hostnameInitParameter = hostnameInitParameter;
  }
  
  public void setProxyUrl(String proxyHost) {
    this.proxyUrl = proxyHost;
  }

  public String getProxyUrl() {
    return proxyUrl;
  }

  /**
   * @return whether to check for a session uid token
   */
  public boolean isCheckUID() {
    return checkUID;
  }

  /**
   * @param checkUID whether to check for a session uid token
   */
  public void setCheckUID(boolean checkUID) {
    this.checkUID = checkUID;
  }

  /**
   * @return name of the session String token to check if {@link #isCheckUID()}
   */
  public String getUidSessionName() {
    return uidSessionName;
  }

  /**
   * @param uidSessionName name of the session String token to check if {@link #isCheckUID()}
   */
  public void setUidSessionName(String uidSessionName) {
    this.uidSessionName = uidSessionName;
  }

  /**
   * @return name of the param to check against {@link #getUidSessionName()}
   */
  public String getUidParamName() {
    return uidParamName;
  }

  /**
   * param uidParamName name of the param to check against {@link #getUidSessionName()}
   */
  public void setUidParamName(String uidParamName) {
    this.uidParamName = uidParamName;
  }

  @Override
  public int getOrder() {
    return order;
  }

  public void setOrder(int order) {
    this.order = order;
  }

  /**
   * 
   */
  @Override
  public Authentication attemptAuthentication(HttpServletRequest request) throws AuthenticationException {
    String browserIdAssertion = request.getParameter(getAssertionParameterName());
    
    if(browserIdAssertion != null) {
     
      BrowserIdVerifier verifier = new BrowserIdVerifier(getVerificationServiceUrl(), getProxyUrl());
      BrowserIdResponse response = null;
      
      String audience  = request.getRequestURL().toString();
      String referer = request.getHeader("Referer");
      //strip to host names
      try {
        URL audienceUrl = new URL(audience);
        audience = audienceUrl.getHost();
        URL refererUrl = new URL(referer);
        referer = refererUrl.getHost();
      } catch (MalformedURLException e) {
        throw new BrowserIdAuthenticationException("Request contains malformed URL", e);
      }
      
      if(!StringUtils.equals(audience, referer) || StringUtils.isEmpty(referer)){
        log.debug("Referer mismatch");
        throw new BrowserIdAuthenticationException("Referer mismatch");
      }
      if(!isAudienceOK(audience, request)) {
        log.debug("Audience mismatch");
        throw new BrowserIdAuthenticationException("Audience mismatch");
      }
      
      if(checkUID){
        String tuid = (String) request.getSession().getAttribute(uidSessionName);
        String pTuid = request.getParameter(uidParamName);
        if(tuid == null || !tuid.equals(pTuid)){
          log.debug("Session UID mismatch");
          throw new BrowserIdAuthenticationException("Session UID mismatch");
        }
      }

      try {
        response = verifier.verify(browserIdAssertion, audience);
      } catch (HttpException e) {
        log.debug("Error calling verify service [" + verifier.getVerifyUrl() + "]", e);
        throw new BrowserIdAuthenticationException("Error calling verify service [" + verifier.getVerifyUrl() + "]", e);
      } catch (IOException e) {
        log.debug("Error calling verify service [" + verifier.getVerifyUrl() + "]", e);
        throw new BrowserIdAuthenticationException("Error calling verify service [" + verifier.getVerifyUrl() + "]", e);
      }

      if(response != null){
        if(response.getStatus() == BrowserIdResponse.Status.OK){
          BrowserIdAuthenticationToken token = new BrowserIdAuthenticationToken(response, browserIdAssertion);
          //send to provider to get authorities
          return getAuthenticationManager().authenticate(token);
        }
        else {
          log.debug("BrowserID verification failed, reason: " + response.getReason());
          throw new BrowserIdAuthenticationException("BrowserID verification failed, reason: " + response.getReason());
        }
      }
      else {
    	  log.debug("Verification yielded null response");
    	  throw new BrowserIdAuthenticationException("Verification yielded null response");
      }
    }
    //may not be a BrowserID authentication
    return null;
  }
  
  public boolean isAudienceOK(String audience, HttpServletRequest request){
    String host = getHost(request);
    if(!StringUtils.isEmpty(host)){
      return StringUtils.equals(audience, host);
    }
    
    return false;
  }

  @Override
  public String getDefaultFilterProcessesUrl() {
    return DEFAULT_FILTER_PROCESS_URL;
  }
  
  
  private String getHost( HttpServletRequest request ){
    String host = null;
    if(!StringUtils.isEmpty(hostname)){
      host = hostname;
    }else if(!StringUtils.isEmpty(hostnameInitParameter)){
      host = request.getSession().getServletContext().getInitParameter(hostnameInitParameter);
    }
    if(StringUtils.contains(host, "/")){
      //base url can be set as domain name or full url
      try {
        URL baseUrl = new URL(host);
        host = baseUrl.getHost();
      } catch (MalformedURLException e) {
        return null;
      }
    }
    return host;
  }
  
  
  @Override
  public void afterPropertiesSet() throws Exception {
    super.afterPropertiesSet();
    //request parameters
    Assert.hasLength(getAssertionParameterName(), "assertionParameterName cannot be empty.");
    //check URL
    Assert.hasLength(getVerificationServiceUrl());
    //check hostname
    Assert.isTrue(!StringUtils.isEmpty(hostname) || !StringUtils.isEmpty(hostnameInitParameter), "either hostname or hostnameInitParameter must be set");
    
    URL url = (new URI(getVerificationServiceUrl())).toURL();
    Assert.isTrue(StringUtils.equalsIgnoreCase(url.getProtocol(), "https"), "verificationServiceUrl does not use a secure protocol");
  }
  
  @Override
  protected void onSuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, Authentication authResult) throws IOException {
    if (authResult instanceof BrowserIdAuthenticationToken) {
      logger.debug(((BrowserIdAuthenticationToken) authResult));
      
    }

  }
}
