package com.knack.ai.llm;

/**
 * Provider-agnostic client for a text-generation LLM.
 */
public interface LlmClient
{
	/**
	 * Sends the prompt to the LLM and returns the generated text.
	 *
	 * @param prompt
	 *           the full prompt
	 * @return generated text, never null
	 * @throws LlmClientException
	 *            if the call fails or returns an unusable response
	 */
	String complete(String prompt);
}

