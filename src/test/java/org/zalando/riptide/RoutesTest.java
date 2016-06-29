package org.zalando.riptide;

/*
 * ⁣​
 * Riptide
 * ⁣⁣
 * Copyright (C) 2015 Zalando SE
 * ⁣⁣
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ​⁣
 */

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.zalando.problem.ThrowableProblem;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasToString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hobsoft.hamcrest.compose.ComposeMatchers.hasFeature;
import static org.junit.Assert.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_LOCATION;
import static org.springframework.http.HttpHeaders.LOCATION;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.Series.SUCCESSFUL;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.zalando.riptide.Bindings.anyStatus;
import static org.zalando.riptide.Bindings.on;
import static org.zalando.riptide.Navigators.series;
import static org.zalando.riptide.Navigators.status;
import static org.zalando.riptide.Routes.contentLocation;
import static org.zalando.riptide.Routes.headers;
import static org.zalando.riptide.Routes.location;
import static org.zalando.riptide.Routes.pass;
import static org.zalando.riptide.Routes.propagate;
import static org.zalando.riptide.Routes.resolveAgainst;

public final class RoutesTest {

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    private final URI url = URI.create("https://api.example.com/accounts/123");

    private final Rest unit;
    private final MockRestServiceServer server;

    public RoutesTest() {
        final MockSetup setup = new MockSetup();
        this.unit = setup.getRest();
        this.server = setup.getServer();
    }

    @Test
    public void shouldPass() throws IOException {
        server.expect(requestTo(url)).andRespond(
                withSuccess());

        unit.get(url)
                .dispatch(status(),
                        on(OK).call(pass()),
                        anyStatus().call(this::fail));
    }

    @Test
    public void shouldMapHeaders() throws ExecutionException, InterruptedException, IOException {
        server.expect(requestTo(url)).andRespond(
                withSuccess()
                        .body(new ClassPathResource("account.json"))
                        .contentType(APPLICATION_JSON));

        final HttpHeaders headers = unit.head(url)
                .dispatch(status(),
                        on(OK).capture(headers()),
                        anyStatus().call(this::fail))
                .get().to(HttpHeaders.class);

        assertThat(headers.toSingleValueMap(), hasEntry("Content-Type", APPLICATION_JSON_VALUE));
    }

    @Test
    public void shouldMapLocation() throws ExecutionException, InterruptedException, IOException {
        final HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create("http://example.org"));

        server.expect(requestTo(url)).andRespond(
                withSuccess()
                        .headers(headers));

        final URI uri = unit.head(url)
                .dispatch(status(),
                        on(OK).capture(location()),
                        anyStatus().call(this::fail))
                .get().to(URI.class);

        assertThat(uri, hasToString("http://example.org"));
    }

    @Test
    public void shouldPropagateException() throws ExecutionException, InterruptedException, IOException {
        server.expect(requestTo(url)).andRespond(
                withStatus(UNPROCESSABLE_ENTITY)
                        .body(new ClassPathResource("problem.json"))
                        .contentType(APPLICATION_JSON));

        exception.expect(ExecutionException.class);
        exception.expectCause(instanceOf(IOException.class));
        exception.expectCause(hasFeature("cause", Throwable::getCause, instanceOf(ThrowableProblem.class)));

        unit.get(url)
                .dispatch(status(),
                        on(UNPROCESSABLE_ENTITY).call(ThrowableProblem.class, propagate()),
                        anyStatus().call(this::fail))
                .get();
    }

    @Test
    public void shouldPropagateIOExceptionAsIs() throws ExecutionException, InterruptedException, IOException {
        server.expect(requestTo(url)).andRespond(
                withStatus(UNPROCESSABLE_ENTITY)
                        .body("{}")
                        .contentType(APPLICATION_JSON));

        exception.expect(ExecutionException.class);
        exception.expectCause(instanceOf(IOException.class));

        unit.get(url)
                .dispatch(status(),
                        on(UNPROCESSABLE_ENTITY).call(IOException.class, propagate()),
                        anyStatus().call(this::fail))
                .get();
    }

    @Test
    public void shouldNotFailIfNothingToResolve() throws ExecutionException, InterruptedException, IOException {
        server.expect(requestTo(url)).andRespond(
                withSuccess());

        final ClientHttpResponse response = unit.get(url)
                .dispatch(series(),
                        on(SUCCESSFUL).capture(resolveAgainst("https://api.example.com/accounts/123")))
                .get().to(ClientHttpResponse.class);

        assertThat(response, hasToString(notNullValue()));
    }

    @Test
    public void shouldResolveLocation() throws ExecutionException, InterruptedException, IOException {
        final HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create("/accounts/456"));
        server.expect(requestTo(url)).andRespond(
                withSuccess().headers(headers));

        final URI location = unit.post(url).headers(new HttpHeaders())
                .dispatch(series(),
                        on(SUCCESSFUL).capture(resolveAgainst(url).andThen(location())))
                .get().to(URI.class);

        assertThat(location, hasToString("https://api.example.com/accounts/456"));
    }

    @Test
    public void shouldResolveContentLocation() throws ExecutionException, InterruptedException, IOException {
        final HttpHeaders headers = new HttpHeaders();
        headers.set(CONTENT_LOCATION, "/accounts/456");
        server.expect(requestTo(url)).andRespond(
                withSuccess().headers(headers));

        final URI location = unit.post(url).body("")
                .dispatch(series(),
                        on(SUCCESSFUL).capture(resolveAgainst(url).andThen(contentLocation())))
                .get().to(URI.class);

        assertThat(location, hasToString("https://api.example.com/accounts/456"));
    }

    @Test
    public void shouldResolveLocationInNestedDispatch() throws ExecutionException, InterruptedException, IOException {
        final HttpHeaders headers = new HttpHeaders();
        headers.set(LOCATION, "/accounts/456");
        server.expect(requestTo(url)).andRespond(
                withSuccess().headers(headers));

        final URI location = unit.post(url).headers(new HttpHeaders()).body("")
                .dispatch(series(),
                        on(SUCCESSFUL).dispatch(resolveAgainst(url), status(),
                                on(OK).capture(location())))
                .get().to(URI.class);

        assertThat(location, hasToString("https://api.example.com/accounts/456"));
    }

    @Test
    public void shouldResolveLocationAndContentLocation() throws ExecutionException, InterruptedException, IOException {
        final HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create("/accounts/456"));
        headers.set(CONTENT_LOCATION, "/accounts/456");
        server.expect(requestTo(url)).andRespond(
                withSuccess().headers(headers));


        final ClientHttpResponse response = unit.get(url)
                .dispatch(series(),
                        on(SUCCESSFUL).dispatch(resolveAgainst(url),
                                RoutingTree.create(status(), on(OK).capture())))
                .get().to(ClientHttpResponse.class);

        assertThat(response.getHeaders().getLocation(), hasToString("https://api.example.com/accounts/456"));
        assertThat(response.getHeaders().getFirst(CONTENT_LOCATION), is("https://api.example.com/accounts/456"));
    }

    private void fail(final ClientHttpResponse response) throws IOException {
        throw new AssertionError(response.getRawStatusCode());
    }

}
