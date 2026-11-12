package com.knack.ai.service.impl;

import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.model.ModelService;

import java.util.List;
import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.knack.ai.llm.LlmClient;
import com.knack.ai.llm.LlmClientException;
import com.knack.ai.prompt.ProductPromptBuilder;
import com.knack.ai.service.GenerationResult;
import com.knack.ai.service.ProductDescriptionGenerationService;


/**
 * Default {@link ProductDescriptionGenerationService}. The LLM writes only plain-text paragraphs; this class escapes
 * them and adds the structure (paragraphs, specification list), so the saved HTML only ever contains p, strong, ul and
 * li tags.
 */
public class DefaultProductDescriptionGenerationService implements ProductDescriptionGenerationService
{
	private static final Logger LOG = LogManager.getLogger(DefaultProductDescriptionGenerationService.class);

	static final String STAGED_VERSION_KEY = "ai.description.staged.version";
	static final String MODEL_KEY = "ai.llm.model";
	static final int MIN_FACTS = 3;
	static final String INSUFFICIENT_DATA = "insufficient data";
	static final String SPECIFICATIONS_HEADING = "Specifications";

	private ModelService modelService;
	private ProductPromptBuilder productPromptBuilder;
	private LlmClient llmClient;
	private ConfigurationService configurationService;

	@Override
	public String generateDescription(final ProductModel product)
	{
		final String raw = llmClient.complete(productPromptBuilder.buildPrompt(product));
		final String text = toPlainText(raw);
		if (text.isEmpty())
		{
			throw new LlmClientException("LLM returned no usable text");
		}
		return text;
	}

	@Override
	public GenerationResult generateAndSave(final ProductModel product, final boolean overwrite)
	{
		final long start = System.currentTimeMillis();
		final String code = product.getCode();
		final String version = product.getCatalogVersion() == null ? null : product.getCatalogVersion().getVersion();
		int facts = -1;

		GenerationResult result;
		try
		{
			final String stagedVersion = configurationService.getConfiguration().getString(STAGED_VERSION_KEY, "Staged");
			if (!stagedVersion.equals(version))
			{
				result = GenerationResult.failed(code, "Refused: only the " + stagedVersion + " catalog version can be written");
			}
			else if (!overwrite && hasDescription(product))
			{
				result = GenerationResult.skipped(code, "Description already exists");
			}
			else
			{
				facts = productPromptBuilder.countFacts(product);
				if (facts < MIN_FACTS)
				{
					result = GenerationResult.skipped(code, INSUFFICIENT_DATA);
				}
				else
				{
					final String text = generateDescription(product);
					product.setDescription(toHtml(text, productPromptBuilder.getSpecifications(product)), Locale.ENGLISH);
					modelService.save(product);
					result = GenerationResult.success(code, "Description saved");
				}
			}
		}
		catch (final RuntimeException e)
		{
			LOG.warn("Description generation failed for product {}", code, e);
			result = GenerationResult.failed(code, e.getMessage());
		}

		LOG.info("AI description audit: product={}, catalogVersion={}, model={}, facts={}, durationMs={}, outcome={}", code,
				version, configurationService.getConfiguration().getString(MODEL_KEY, ""), facts,
				System.currentTimeMillis() - start, result.getStatus());
		return result;
	}

	protected boolean hasDescription(final ProductModel product)
	{
		final String existing = product.getDescription(Locale.ENGLISH);
		return existing != null && !existing.isBlank();
	}

	/**
	 * Strips any HTML tags and collapses stray whitespace while keeping paragraph breaks.
	 */
	protected String toPlainText(final String raw)
	{
		if (raw == null)
		{
			return "";
		}
		return raw.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("(?i)</p>", "\n\n").replaceAll("<[^>]*>", "")
				.replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
	}

	/**
	 * Builds the stored HTML: one p per paragraph, then a bold "Specifications" heading and a bullet list when
	 * specifications exist. All text is escaped.
	 */
	protected String toHtml(final String plainText, final List<String> specifications)
	{
		final StringBuilder html = new StringBuilder();
		for (final String paragraph : plainText.split("\\n{2,}"))
		{
			final String clean = paragraph.replaceAll("\\s*\\n\\s*", " ").trim();
			if (!clean.isEmpty())
			{
				html.append("<p>").append(escapeHtml(clean)).append("</p>");
			}
		}
		if (specifications != null && !specifications.isEmpty())
		{
			html.append("<p><strong>").append(SPECIFICATIONS_HEADING).append("</strong></p><ul>");
			for (final String specification : specifications)
			{
				html.append("<li>").append(escapeHtml(specification)).append("</li>");
			}
			html.append("</ul>");
		}
		return html.toString();
	}

	protected String escapeHtml(final String value)
	{
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
				.replace("'", "&#39;");
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setProductPromptBuilder(final ProductPromptBuilder productPromptBuilder)
	{
		this.productPromptBuilder = productPromptBuilder;
	}

	public void setLlmClient(final LlmClient llmClient)
	{
		this.llmClient = llmClient;
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}
}

