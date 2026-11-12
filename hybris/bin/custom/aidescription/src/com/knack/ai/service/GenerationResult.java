package com.knack.ai.service;

/**
 * Outcome of a description generation attempt.
 */
public class GenerationResult
{
	public enum Status
	{
		SUCCESS, SKIPPED, FAILED
	}

	private final Status status;
	private final String productCode;
	private final String message;

	public GenerationResult(final Status status, final String productCode, final String message)
	{
		this.status = status;
		this.productCode = productCode;
		this.message = message;
	}

	public static GenerationResult success(final String productCode, final String message)
	{
		return new GenerationResult(Status.SUCCESS, productCode, message);
	}

	public static GenerationResult skipped(final String productCode, final String message)
	{
		return new GenerationResult(Status.SKIPPED, productCode, message);
	}

	public static GenerationResult failed(final String productCode, final String message)
	{
		return new GenerationResult(Status.FAILED, productCode, message);
	}

	public Status getStatus()
	{
		return status;
	}

	public String getProductCode()
	{
		return productCode;
	}

	public String getMessage()
	{
		return message;
	}

	@Override
	public String toString()
	{
		return "GenerationResult[status=" + status + ", product=" + productCode + ", message=" + message + "]";
	}
}

