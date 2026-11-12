package com.knack.ai.llm.impl;

import de.hybris.platform.servicelayer.config.ConfigurationService;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knack.ai.llm.LlmClient;
import com.knack.ai.llm.LlmClientException;


/**
 * {@link LlmClient} for a local Ollama server (/api/generate, non-streaming).
 */
public class OllamaLlmClient implements LlmClient
{
	private static final Logger LOG = LogManager.getLogger(OllamaLlmClient.class);

	static final String URL_KEY = "ai.llm.url";
	static final String MODEL_KEY = "ai.llm.model";
	static final String TIMEOUT_KEY = "ai.llm.timeout.seconds";
	static final String DEFAULT_URL = "http://localhost:11434/api/generate";
	static final String DEFAULT_MODEL = "qwen2.5:7b";
	static final int DEFAULT_TIMEOUT_SECONDS = 60;

	private final ObjectMapper mapper = new ObjectMapper();
	private ConfigurationService configurationService;
	private HttpClient httpClient;

	@Override
	public String complete(final String prompt)
	{
		final String url = configurationService.getConfiguration().getString(URL_KEY, DEFAULT_URL);
		final String model = configurationService.getConfiguration().getString(MODEL_KEY, DEFAULT_MODEL);
		final int timeout = configurationService.getConfiguration().getInt(TIMEOUT_KEY, DEFAULT_TIMEOUT_SECONDS);

		try
		{
			final Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("model", model);
			payload.put("prompt", prompt);
			payload.put("stream", false);

			final HttpRequest request = HttpRequest.newBuilder(URI.create(url))
					.timeout(Duration.ofSeconds(timeout))
					.header("content-type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
					.build();

			final HttpResponse<String> response = getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() != 200)
			{
				throw new LlmClientException("LLM returned HTTP " + response.statusCode() + " from " + url);
			}

			final JsonNode text = mapper.readTree(response.body()).get("response");
			if (text == null || text.asText().isBlank())
			{
				throw new LlmClientException("LLM response contained no 'response' text");
			}
			return text.asText().trim();
		}
		catch (final IOException e)
		{
			throw new LlmClientException("LLM call failed: " + e.getMessage(), e);
		}
		catch (final InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new LlmClientException("LLM call interrupted", e);
		}
	}

	private synchronized HttpClient getHttpClient()
	{
		if (httpClient == null)
		{
			httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
		}
		return httpClient;
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}
}

