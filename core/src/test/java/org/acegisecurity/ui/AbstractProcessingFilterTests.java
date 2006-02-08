/* Copyright 2004, 2005 Acegi Technology Pty Limited
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

package org.acegisecurity.ui;

import junit.framework.TestCase;

import org.acegisecurity.AccountExpiredException;
import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.BadCredentialsException;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.GrantedAuthorityImpl;
import org.acegisecurity.MockAuthenticationManager;
import org.acegisecurity.context.SecurityContextHolder;
import org.acegisecurity.context.SecurityContextImpl;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.acegisecurity.ui.rememberme.TokenBasedRememberMeServices;

import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import java.util.Properties;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * Tests {@link AbstractProcessingFilter}.
 *
 * @author Ben Alex
 * @version $Id$
 */
public class AbstractProcessingFilterTests extends TestCase {
    //~ Constructors ===========================================================

    public AbstractProcessingFilterTests() {
        super();
    }

    public AbstractProcessingFilterTests(String arg0) {
        super(arg0);
    }

    //~ Methods ================================================================

    public static void main(String[] args) {
        junit.textui.TestRunner.run(AbstractProcessingFilterTests.class);
    }

    public void testDefaultProcessesFilterUrlWithPathParameter() {
        MockHttpServletRequest request = createMockRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockAbstractProcessingFilter filter = new MockAbstractProcessingFilter();
        filter.setFilterProcessesUrl("/j_acegi_security_check");

        request.setRequestURI(
            "/mycontext/j_acegi_security_check;jsessionid=I8MIONOSTHOR");
        assertTrue(filter.requiresAuthentication(request, response));
    }

    public void testDoFilterWithNonHttpServletRequestDetected()
        throws Exception {
        AbstractProcessingFilter filter = new MockAbstractProcessingFilter();

        try {
            filter.doFilter(null, new MockHttpServletResponse(),
                new MockFilterChain());
            fail("Should have thrown ServletException");
        } catch (ServletException expected) {
            assertEquals("Can only process HttpServletRequest",
                expected.getMessage());
        }
    }

    public void testDoFilterWithNonHttpServletResponseDetected()
        throws Exception {
        AbstractProcessingFilter filter = new MockAbstractProcessingFilter();

        try {
            filter.doFilter(new MockHttpServletRequest(null, null), null,
                new MockFilterChain());
            fail("Should have thrown ServletException");
        } catch (ServletException expected) {
            assertEquals("Can only process HttpServletResponse",
                expected.getMessage());
        }
    }

    public void testFailedAuthenticationRedirectsAppropriately()
        throws Exception {
        // Setup our HTTP request
        MockHttpServletRequest request = createMockRequest();

        // Setup our filter configuration
        MockFilterConfig config = new MockFilterConfig(null);

        // Setup our expectation that the filter chain will not be invoked, as we redirect to authenticationFailureUrl
        MockFilterChain chain = new MockFilterChain(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Setup our test object, to deny access
        MockAbstractProcessingFilter filter = new MockAbstractProcessingFilter(false);
        filter.setAuthenticationFailureUrl("/myApp/failed.jsp");

        // Test
        executeFilterInContainerSimulator(config, filter, request, response,
            chain);

        assertEquals("/myApp/failed.jsp", response.getRedirectedUrl());
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        //Prepare again, this time using the exception mapping
        filter = new MockAbstractProcessingFilter(new AccountExpiredException(
                    "You're account is expired"));
        filter.setAuthenticationFailureUrl("/myApp/failed.jsp");

        Properties exceptionMappings = filter.getExceptionMappings();
        exceptionMappings.setProperty(AccountExpiredException.class.getName(),
            "/myApp/accountExpired.jsp");
        filter.setExceptionMappings(exceptionMappings);
        response = new MockHttpServletResponse();

        // Test
        executeFilterInContainerSimulator(config, filter, request, response,
            chain);

        assertEquals("/myApp/accountExpired.jsp", response.getRedirectedUrl());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    public void testFilterProcessesUrlVariationsRespected()
        throws Exception {
        // Setup our HTTP request
        MockHttpServletRequest request = createMockRequest();
        request.setServletPath("/j_OTHER_LOCATION");
        request.setRequestURI("/mycontext/j_OTHER_LOCATION");

        // Setup our filter configuration
        MockFilterConfig config = new MockFilterConfig(null);

        // Setup our expectation that the filter chain will not be invoked, as we redirect to defaultTargetUrl
        MockFilterChain chain = new MockFilterChain(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Setup our test object, to grant access
        MockAbstractProcessingFilter filter = new MockAbstractProcessingFilter(true);
        filter.setFilterProcessesUrl("/j_OTHER_LOCATION");
        filter.setDefaultTargetUrl("/logged_in.jsp");

        // Test
        executeFilterInContainerSimulator(config, filter, request, response,
            chain);
        assertEquals("/logged_in.jsp", response.getRedirectedUrl());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("test",
            SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                                 .toString());
    }

    public void testGettersSetters() {
        AbstractProcessingFilter filter = new MockAbstractProcessingFilter();
        assertNotNull(filter.getRememberMeServices());
        filter.setRememberMeServices(new TokenBasedRememberMeServices());
        assertEquals(TokenBasedRememberMeServices.class,
            filter.getRememberMeServices().getClass());

        filter.setAuthenticationFailureUrl("/x");
        assertEquals("/x", filter.getAuthenticationFailureUrl());

        filter.setAuthenticationManager(new MockAuthenticationManager());
        assertTrue(filter.getAuthenticationManager() != null);

        filter.setDefaultTargetUrl("/default");
        assertEquals("/default", filter.getDefaultTargetUrl());

        filter.setFilterProcessesUrl("/p");
        assertEquals("/p", filter.getFilterProcessesUrl());

        filter.setAuthenticationFailureUrl("/fail");
        assertEquals("/fail", filter.getAuthenticationFailureUrl());
    }

    public void testIgnoresAnyServletPathOtherThanFilterProcessesUrl()
        throws Exception {
        // Setup our HTTP request
        MockHttpServletRequest request = createMockRequest();
        request.setServletPath("/some.file.html");
        request.setRequestURI("/mycontext/some.file.html");

        // Setup our filter configuration
        MockFilterConfig config = new MockFilterConfig(null);

        // Setup our expectation that the filter chain will be invoked, as our request is for a page the filter isn't monitoring
        MockFilterChain chain = new MockFilterChain(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Setup our test object, to deny access
        MockAbstractProcessingFilter filter = new MockAbstractProcessingFilter(false);

        // Test
        executeFilterInContainerSimulator(config, filter, request, response,
            chain);
    }

    public void testNormalOperationWithDefaultFilterProcessesUrl()
        throws Exception {
        // Setup our HTTP request
        MockHttpServletRequest request = createMockRequest();

        // Setup our filter configuration
        MockFilterConfig config = new MockFilterConfig(null);

        // Setup our expectation that the filter chain will not be invoked, as we redirect to defaultTargetUrl
        MockFilterChain chain = new MockFilterChain(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Setup our test object, to grant access
        MockAbstractProcessingFilter filter = new MockAbstractProcessingFilter(true);
        filter.setFilterProcessesUrl("/j_mock_post");
        filter.setDefaultTargetUrl("/logged_in.jsp");
        filter.setAuthenticationFailureUrl("/failure.jsp");
        filter.setAuthenticationManager(new MockAuthenticationManager(true));
        filter.afterPropertiesSet();

        // Test
        executeFilterInContainerSimulator(config, filter, request, response,
            chain);
        assertEquals("/logged_in.jsp", response.getRedirectedUrl());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("test",
            SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                                 .toString());
    }

    public void testStartupDetectsInvalidAuthenticationFailureUrl()
        throws Exception {
        AbstractProcessingFilter filter = new MockAbstractProcessingFilter();
        filter.setAuthenticationManager(new MockAuthenticationManager());
        filter.setDefaultTargetUrl("/");
        filter.setFilterProcessesUrl("/j_acegi_security_check");

        try {
            filter.afterPropertiesSet();
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertEquals("authenticationFailureUrl must be specified",
                expected.getMessage());
        }
    }

    public void testStartupDetectsInvalidAuthenticationManager()
        throws Exception {
        AbstractProcessingFilter filter = new MockAbstractProcessingFilter();
        filter.setAuthenticationFailureUrl("/failed.jsp");
        filter.setDefaultTargetUrl("/");
        filter.setFilterProcessesUrl("/j_acegi_security_check");

        try {
            filter.afterPropertiesSet();
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertEquals("authenticationManager must be specified",
                expected.getMessage());
        }
    }

    public void testStartupDetectsInvalidDefaultTargetUrl()
        throws Exception {
        AbstractProcessingFilter filter = new MockAbstractProcessingFilter();
        filter.setAuthenticationFailureUrl("/failed.jsp");
        filter.setAuthenticationManager(new MockAuthenticationManager());
        filter.setFilterProcessesUrl("/j_acegi_security_check");

        try {
            filter.afterPropertiesSet();
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertEquals("defaultTargetUrl must be specified",
                expected.getMessage());
        }
    }

    public void testStartupDetectsInvalidFilterProcessesUrl()
        throws Exception {
        AbstractProcessingFilter filter = new MockAbstractProcessingFilter();
        filter.setAuthenticationFailureUrl("/failed.jsp");
        filter.setAuthenticationManager(new MockAuthenticationManager());
        filter.setDefaultTargetUrl("/");
        filter.setFilterProcessesUrl(null);

        try {
            filter.afterPropertiesSet();
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertEquals("filterProcessesUrl must be specified",
                expected.getMessage());
        }
    }

    public void testSuccessLoginThenFailureLoginResultsInSessionLosingToken()
        throws Exception {
        // Setup our HTTP request
        MockHttpServletRequest request = createMockRequest();

        // Setup our filter configuration
        MockFilterConfig config = new MockFilterConfig(null);

        // Setup our expectation that the filter chain will not be invoked, as we redirect to defaultTargetUrl
        MockFilterChain chain = new MockFilterChain(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Setup our test object, to grant access
        MockAbstractProcessingFilter filter = new MockAbstractProcessingFilter(true);
        filter.setFilterProcessesUrl("/j_mock_post");
        filter.setDefaultTargetUrl("/logged_in.jsp");

        // Test
        executeFilterInContainerSimulator(config, filter, request, response,
            chain);
        assertEquals("/logged_in.jsp", response.getRedirectedUrl());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("test",
            SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                                 .toString());

        // Now try again but this time have filter deny access
        // Setup our HTTP request
        // Setup our expectation that the filter chain will not be invoked, as we redirect to authenticationFailureUrl
        chain = new MockFilterChain(false);
        response = new MockHttpServletResponse();

        // Setup our test object, to deny access
        filter = new MockAbstractProcessingFilter(false);
        filter.setFilterProcessesUrl("/j_mock_post");
        filter.setAuthenticationFailureUrl("/failed.jsp");

        // Test
        executeFilterInContainerSimulator(config, filter, request, response,
            chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    public void testSuccessfulAuthenticationButWithAlwaysUseDefaultTargetUrlCausesRedirectToDefaultTargetUrl()
        throws Exception {
        // Setup our HTTP request
        MockHttpServletRequest request = createMockRequest();
        request.getSession().setAttribute(AbstractProcessingFilter.ACEGI_SECURITY_TARGET_URL_KEY,
            "/my-destination");

        // Setup our filter configuration
        MockFilterConfig config = new MockFilterConfig(null);

        // Setup our expectation that the filter chain will be invoked, as we want to go to the location requested in the session
        MockFilterChain chain = new MockFilterChain(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Setup our test object, to grant access
        MockAbstractProcessingFilter filter = new MockAbstractProcessingFilter(true);
        filter.setFilterProcessesUrl("/j_mock_post");
        filter.setDefaultTargetUrl("/foobar");
        assertFalse(filter.isAlwaysUseDefaultTargetUrl()); // check default
        filter.setAlwaysUseDefaultTargetUrl(true);
        assertTrue(filter.isAlwaysUseDefaultTargetUrl()); // check changed

        // Test
        executeFilterInContainerSimulator(config, filter, request, response,
            chain);
        assertEquals("/foobar", response.getRedirectedUrl());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    public void testSuccessfulAuthenticationCausesRedirectToSessionSpecifiedUrl()
        throws Exception {
        // Setup our HTTP request
        MockHttpServletRequest request = createMockRequest();
        request.getSession().setAttribute(AbstractProcessingFilter.ACEGI_SECURITY_TARGET_URL_KEY,
            "/my-destination");

        // Setup our filter configuration
        MockFilterConfig config = new MockFilterConfig(null);

        // Setup our expectation that the filter chain will be invoked, as we want to go to the location requested in the session
        MockFilterChain chain = new MockFilterChain(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Setup our test object, to grant access
        MockAbstractProcessingFilter filter = new MockAbstractProcessingFilter(true);
        filter.setFilterProcessesUrl("/j_mock_post");

        // Test
        executeFilterInContainerSimulator(config, filter, request, response,
            chain);
        assertEquals("/my-destination", response.getRedirectedUrl());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    protected void setUp() throws Exception {
        super.setUp();
        SecurityContextHolder.clearContext();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest createMockRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        request.setServletPath("/j_mock_post");
        request.setScheme("http");
        request.setServerName("www.example.com");
        request.setRequestURI("/mycontext/j_mock_post");

        return request;
    }

    private void executeFilterInContainerSimulator(FilterConfig filterConfig,
        Filter filter, ServletRequest request, ServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {
        filter.init(filterConfig);
        filter.doFilter(request, response, filterChain);
        filter.destroy();
    }

    //~ Inner Classes ==========================================================

    private class MockAbstractProcessingFilter extends AbstractProcessingFilter {
        private AuthenticationException exceptionToThrow;
        private boolean grantAccess;

        public MockAbstractProcessingFilter(boolean grantAccess) {
            this.grantAccess = grantAccess;
            this.exceptionToThrow = new BadCredentialsException(
                    "Mock requested to do so");
        }

        public MockAbstractProcessingFilter(
            AuthenticationException exceptionToThrow) {
            this.grantAccess = false;
            this.exceptionToThrow = exceptionToThrow;
        }

        private MockAbstractProcessingFilter() {
            super();
        }

        public String getDefaultFilterProcessesUrl() {
            return "/j_mock_post";
        }

        public Authentication attemptAuthentication(HttpServletRequest request)
            throws AuthenticationException {
            if (grantAccess) {
                return new UsernamePasswordAuthenticationToken("test", "test",
                    new GrantedAuthority[] {new GrantedAuthorityImpl("TEST")});
            } else {
                throw exceptionToThrow;
            }
        }

        public void init(FilterConfig arg0) throws ServletException {}

        public boolean requiresAuthentication(HttpServletRequest request,
            HttpServletResponse response) {
            return super.requiresAuthentication(request, response);
        }
    }

    private class MockFilterChain implements FilterChain {
        private boolean expectToProceed;

        public MockFilterChain(boolean expectToProceed) {
            this.expectToProceed = expectToProceed;
        }

        private MockFilterChain() {
            super();
        }

        public void doFilter(ServletRequest request, ServletResponse response)
            throws IOException, ServletException {
            if (expectToProceed) {
                assertTrue(true);
            } else {
                fail("Did not expect filter chain to proceed");
            }
        }
    }
}
