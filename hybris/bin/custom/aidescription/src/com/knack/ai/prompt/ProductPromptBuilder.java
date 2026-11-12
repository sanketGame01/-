package com.knack.ai.prompt;

import de.hybris.platform.core.model.product.ProductModel;

import java.util.List;


/**
 * Builds the LLM prompt for a product using only facts stored on the product.
 */
public interface ProductPromptBuilder
{
	/**
	 * @param product
	 *           the product to describe
	 * @return the full prompt text; its requested length depends on {@link #countFacts(ProductModel)}
	 */
	String buildPrompt(ProductModel product);

	/**
	 * Counts the facts available for the product: specifications with values, plus 3 if an existing English
	 * description is present.
	 *
	 * @param product
	 *           the product
	 * @return number of facts
	 */
	int countFacts(ProductModel product);

	/**
	 * Returns the product's specifications as display lines such as "Weight: 320 g". Features without values and the
	 * ignored data-sheet feature are left out.
	 *
	 * @param product
	 *           the product
	 * @return specification lines, never null
	 */
	List<String> getSpecifications(ProductModel product);
}

