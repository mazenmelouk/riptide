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

import com.google.common.reflect.TypeToken;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.zalando.riptide.model.Account;
import org.zalando.riptide.model.AccountBody;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.zalando.riptide.Bindings.anyStatus;
import static org.zalando.riptide.Bindings.on;
import static org.zalando.riptide.Navigators.status;

public final class CaptureTest {

    private final URI url = URI.create("https://api.example.com/accounts/123");

    private final Rest unit;
    private final MockRestServiceServer server;

    public CaptureTest() {
        final MockSetup setup = new MockSetup();
        this.unit = setup.getRest();
        this.server = setup.getServer();
    }

    @Test
    public void shouldCaptureResponse() throws IOException, ExecutionException, InterruptedException {
        server.expect(requestTo(url)).andRespond(
                withSuccess()
                        .body(new ClassPathResource("account.json"))
                        .contentType(APPLICATION_JSON));

        final ClientHttpResponse response = unit.get(url)
                .dispatch(status(),
                        on(OK).capture(),
                        anyStatus().call(this::fail))
                .get().to(ClientHttpResponse.class);

        assertThat(response.getStatusCode(), is(OK));
        assertThat(response.getHeaders().getContentType(), is(APPLICATION_JSON));
    }

    @Test
    public void shouldCaptureEntity() throws ExecutionException, InterruptedException, IOException {
        server.expect(requestTo(url)).andRespond(
                withSuccess()
                        .body(new ClassPathResource("account.json"))
                        .contentType(APPLICATION_JSON));

        final AccountBody account = unit.get(url)
                .dispatch(status(),
                        on(OK).capture(TypeToken.of(AccountBody.class)),
                        anyStatus().call(this::fail))
                .get().to(AccountBody.class);

        assertThat(account.getId(), is("1234567890"));
        assertThat(account.getName(), is("Acme Corporation"));
    }

    @Test
    public void shouldCaptureMappedEntity() throws ExecutionException, InterruptedException, IOException {
        final String revision = '"' + "1aa9520a-0cdd-11e5-aa27-8361dd72e660" + '"';

        final HttpHeaders headers = new HttpHeaders();
        headers.setETag(revision);

        server.expect(requestTo(url)).andRespond(
                withSuccess()
                        .body(new ClassPathResource("account.json"))
                        .contentType(APPLICATION_JSON)
                        .headers(headers));

        final Account account = unit.get(url)
                .dispatch(status(),
                        on(OK).capture(AccountBody.class, this::extract),
                        anyStatus().call(this::fail))
                .get().to(Account.class);

        assertThat(account.getId(), is("1234567890"));
        assertThat(account.getRevision(), is(revision));
        assertThat(account.getName(), is("Acme Corporation"));
    }

    private Account extract(final ResponseEntity<AccountBody> entity) {
        final AccountBody account = entity.getBody();
        final String revision = entity.getHeaders().getETag();
        return new Account(account.getId(), revision, account.getName());
    }

    private void fail(final ClientHttpResponse response) throws IOException {
        throw new AssertionError(response.getRawStatusCode());
    }

}
