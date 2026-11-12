package com.knack.ai.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.cronjob.enums.CronJobResult;
import de.hybris.platform.cronjob.enums.CronJobStatus;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.cronjob.PerformResult;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.SearchResult;

import java.util.List;

import org.apache.commons.configuration2.Configuration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import com.knack.ai.model.GenerateDescriptionsCronJobModel;
import com.knack.ai.service.GenerationResult;
import com.knack.ai.service.ProductDescriptionGenerationService;


@UnitTest
@RunWith(MockitoJUnitRunner.Silent.class)
public class GenerateDescriptionsJobTest
{
    private final GenerateDescriptionsJob job = new GenerateDescriptionsJob();

	private ProductDescriptionGenerationService generationService;
	private FlexibleSearchService searchService;
	private GenerateDescriptionsCronJobModel cronJob;
	private ProductModel p1;
	private ProductModel p2;
	private ProductModel p3;

	@Before
	public void setUp()
	{
		generationService = mock(ProductDescriptionGenerationService.class);
		searchService = mock(FlexibleSearchService.class);
		cronJob = mock(GenerateDescriptionsCronJobModel.class);
		p1 = mock(ProductModel.class);
		p2 = mock(ProductModel.class);
		p3 = mock(ProductModel.class);

		final ConfigurationService configurationService = mock(ConfigurationService.class);
		final Configuration configuration = mock(Configuration.class);
		when(configurationService.getConfiguration()).thenReturn(configuration);
		when(configuration.getString("ai.description.staged.version", "Staged")).thenReturn("Staged");

		job.setProductDescriptionGenerationService(generationService);
		job.setFlexibleSearchService(searchService);
		job.setConfigurationService(configurationService);
        job.setModelService(mock(ModelService.class));
        when(cronJob.getRequestAbort()).thenReturn(Boolean.FALSE);

		when(cronJob.getCatalogId()).thenReturn("electronicsProductCatalog");
		when(cronJob.getOverwrite()).thenReturn(Boolean.FALSE);
		when(cronJob.getBatchSize()).thenReturn(10);
		when(cronJob.getMaxProducts()).thenReturn(0);

		final SearchResult<ProductModel> page = searchResult(List.of(p1, p2, p3));
		final SearchResult<ProductModel> empty = searchResult(List.of());
		when(searchService.<ProductModel> search(any(FlexibleSearchQuery.class))).thenReturn(page, empty);
	}

	@Test
	public void shouldProcessAllProductsAndFinish()
	{
		when(generationService.generateAndSave(any(ProductModel.class), eq(false)))
				.thenReturn(GenerationResult.success("1", "ok"), GenerationResult.skipped("2", "exists"),
						GenerationResult.failed("3", "err"));

		final PerformResult result = job.perform(cronJob);

		assertThat(result.getStatus()).isEqualTo(CronJobStatus.FINISHED);
		assertThat(result.getResult()).isEqualTo(CronJobResult.SUCCESS);
		verify(generationService, times(3)).generateAndSave(any(ProductModel.class), eq(false));
	}

	@Test
	public void shouldStopAtMaxProducts()
	{
		when(cronJob.getMaxProducts()).thenReturn(2);
		when(generationService.generateAndSave(any(ProductModel.class), eq(false)))
				.thenReturn(GenerationResult.success("1", "ok"));

		job.perform(cronJob);

		verify(generationService, times(2)).generateAndSave(any(ProductModel.class), eq(false));
		verify(generationService, never()).generateAndSave(eq(p3), eq(false));
	}

	@Test
	public void shouldAbortWhenRequested()
	{
        when(cronJob.getRequestAbort()).thenReturn(Boolean.TRUE);

		final PerformResult result = job.perform(cronJob);

		assertThat(result.getStatus()).isEqualTo(CronJobStatus.ABORTED);
		verify(generationService, never()).generateAndSave(any(ProductModel.class), eq(false));
	}

	@SuppressWarnings("unchecked")
	private static SearchResult<ProductModel> searchResult(final List<ProductModel> products)
	{
		final SearchResult<ProductModel> searchResult = mock(SearchResult.class);
		when(searchResult.getResult()).thenReturn(products);
		return searchResult;
	}
}

