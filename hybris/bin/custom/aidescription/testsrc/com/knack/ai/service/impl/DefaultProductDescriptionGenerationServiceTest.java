package com.knack.ai.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.catalog.model.CatalogVersionModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.model.ModelService;

import java.util.List;
import java.util.Locale;

import org.apache.commons.configuration2.Configuration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import com.knack.ai.llm.LlmClient;
import com.knack.ai.llm.LlmClientException;
import com.knack.ai.prompt.ProductPromptBuilder;
import com.knack.ai.service.GenerationResult;
import com.knack.ai.service.GenerationResult.Status;


@UnitTest
@RunWith(MockitoJUnitRunner.Silent.class)
public class DefaultProductDescriptionGenerationServiceTest
{
	private final DefaultProductDescriptionGenerationService service = new DefaultProductDescriptionGenerationService();

	private ModelService modelService;
	private ProductPromptBuilder promptBuilder;
	private LlmClient llmClient;
	private ProductModel product;
	private CatalogVersionModel catalogVersion;

	@Before
	public void setUp()
	{
		modelService = mock(ModelService.class);
		promptBuilder = mock(ProductPromptBuilder.class);
		llmClient = mock(LlmClient.class);
		product = mock(ProductModel.class);
		catalogVersion = mock(CatalogVersionModel.class);

		final ConfigurationService configurationService = mock(ConfigurationService.class);
		final Configuration configuration = mock(Configuration.class);
		when(configurationService.getConfiguration()).thenReturn(configuration);
		when(configuration.getString("ai.description.staged.version", "Staged")).thenReturn("Staged");
		when(configuration.getString("ai.llm.model", "")).thenReturn("test-model");

		service.setModelService(modelService);
		service.setProductPromptBuilder(promptBuilder);
		service.setLlmClient(llmClient);
		service.setConfigurationService(configurationService);

		when(product.getCode()).thenReturn("P1");
		when(product.getCatalogVersion()).thenReturn(catalogVersion);
		when(catalogVersion.getVersion()).thenReturn("Staged");
		when(promptBuilder.buildPrompt(product)).thenReturn("prompt");
		when(promptBuilder.countFacts(product)).thenReturn(5);
		when(promptBuilder.getSpecifications(product)).thenReturn(List.of());
	}

	@Test
	public void shouldSaveParagraphOnStagedAndStripModelHtml()
	{
		when(product.getDescription(Locale.ENGLISH)).thenReturn(null);
		when(llmClient.complete("prompt")).thenReturn("<p>Nice <b>tripod</b></p>");

		final GenerationResult result = service.generateAndSave(product, false);

		assertThat(result.getStatus()).isEqualTo(Status.SUCCESS);
		verify(product).setDescription("<p>Nice tripod</p>", Locale.ENGLISH);
		verify(modelService).save(product);
	}

	@Test
	public void shouldSplitParagraphsAndAppendSpecificationList()
	{
		when(llmClient.complete("prompt")).thenReturn("First part.\n\nSecond part.");
		when(promptBuilder.getSpecifications(product)).thenReturn(List.of("Weight: 320 g", "Color: Black"));

		service.generateAndSave(product, true);

		verify(product).setDescription("<p>First part.</p><p>Second part.</p>"
				+ "<p><strong>Specifications</strong></p><ul><li>Weight: 320 g</li><li>Color: Black</li></ul>", Locale.ENGLISH);
	}

	@Test
	public void shouldEscapeSpecialCharactersInTextAndSpecifications()
	{
		when(llmClient.complete("prompt")).thenReturn("Fish & Chips");
		when(promptBuilder.getSpecifications(product)).thenReturn(List.of("Name: \"A\" & <B>"));

		service.generateAndSave(product, true);

		verify(product).setDescription("<p>Fish &amp; Chips</p><p><strong>Specifications</strong></p>"
				+ "<ul><li>Name: &quot;A&quot; &amp; &lt;B&gt;</li></ul>", Locale.ENGLISH);
	}

	@Test
	public void shouldSkipWhenDescriptionExistsAndNoOverwrite()
	{
		when(product.getDescription(Locale.ENGLISH)).thenReturn("Existing");

		final GenerationResult result = service.generateAndSave(product, false);

		assertThat(result.getStatus()).isEqualTo(Status.SKIPPED);
		verify(llmClient, never()).complete(anyString());
		verify(modelService, never()).save(product);
	}

	@Test
	public void shouldSkipWithoutCallingLlmWhenFewerThanThreeFacts()
	{
		when(promptBuilder.countFacts(product)).thenReturn(2);

		final GenerationResult result = service.generateAndSave(product, true);

		assertThat(result.getStatus()).isEqualTo(Status.SKIPPED);
		assertThat(result.getMessage()).isEqualTo("insufficient data");
		verify(llmClient, never()).complete(anyString());
		verify(modelService, never()).save(product);
	}

	@Test
	public void shouldGenerateWithExactlyThreeFacts()
	{
		when(promptBuilder.countFacts(product)).thenReturn(3);
		when(llmClient.complete("prompt")).thenReturn("Short text");

		assertThat(service.generateAndSave(product, true).getStatus()).isEqualTo(Status.SUCCESS);
	}

	@Test
	public void shouldOverwriteWhenRequested()
	{
		when(product.getDescription(Locale.ENGLISH)).thenReturn("Existing");
		when(llmClient.complete("prompt")).thenReturn("New text");

		final GenerationResult result = service.generateAndSave(product, true);

		assertThat(result.getStatus()).isEqualTo(Status.SUCCESS);
		verify(product).setDescription("<p>New text</p>", Locale.ENGLISH);
	}

	@Test
	public void shouldFailWhenLlmThrows()
	{
		when(llmClient.complete("prompt")).thenThrow(new LlmClientException("boom"));

		final GenerationResult result = service.generateAndSave(product, true);

		assertThat(result.getStatus()).isEqualTo(Status.FAILED);
		assertThat(result.getMessage()).contains("boom");
		verify(modelService, never()).save(product);
	}

	@Test
	public void shouldFailWhenLlmReturnsOnlyMarkup()
	{
		when(llmClient.complete("prompt")).thenReturn("<br/>  ");

		assertThat(service.generateAndSave(product, true).getStatus()).isEqualTo(Status.FAILED);
		verify(modelService, never()).save(product);
	}

	@Test
	public void shouldRefuseOnline()
	{
		when(catalogVersion.getVersion()).thenReturn("Online");

		final GenerationResult result = service.generateAndSave(product, true);

		assertThat(result.getStatus()).isEqualTo(Status.FAILED);
		verify(llmClient, never()).complete(anyString());
		verify(modelService, never()).save(product);
	}
}

