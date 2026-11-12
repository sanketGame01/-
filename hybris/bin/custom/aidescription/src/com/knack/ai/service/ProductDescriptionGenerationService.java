package com.knack.ai.service;

import de.hybris.platform.core.model.product.ProductModel;


/**
 * Generates product descriptions with an LLM.
 */
public interface ProductDescriptionGenerationService
{
	/**
	 * Generates a plain-text description. Does not save anything.
	 *
	 * @param product
	 *           the product
	 * @return generated plain text
	 * @throws com.knack.ai.llm.LlmClientException
	 *            if the LLM call fails or returns unusable text
	 */
	String generateDescription(ProductModel product);

	/**
	 * Generates a description and saves it as the English description of a Staged product. Never throws for
	 * expected failures; the outcome is reported in the result.
	 *
	 * @param product
	 *           the product (must be in the Staged catalog version)
	 * @param overwrite
	 *           if false, products that already have a description are skipped
	 * @return the outcome
	 */
	GenerationResult generateAndSave(ProductModel product, boolean overwrite);
}

