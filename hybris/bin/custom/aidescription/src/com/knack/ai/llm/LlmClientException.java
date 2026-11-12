package com.knack.ai.llm;

/**
 * Thrown when an LLM call fails.
 */
public class LlmClientException extends RuntimeException
{
	public LlmClientException(final String message)
	{
		super(message);
	}

	public LlmClientException(final String message, final Throwable cause)
	{
		super(message, cause);
	}
}

