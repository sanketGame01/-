package com.knack.ai.job;

import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.cronjob.enums.CronJobResult;
import de.hybris.platform.cronjob.enums.CronJobStatus;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.cronjob.AbstractJobPerformable;
import de.hybris.platform.servicelayer.cronjob.PerformResult;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.knack.ai.model.GenerateDescriptionsCronJobModel;
import com.knack.ai.service.GenerationResult;
import com.knack.ai.service.ProductDescriptionGenerationService;


/**
 * Generates descriptions for Staged products of one catalog, page by page.
 */
public class GenerateDescriptionsJob extends AbstractJobPerformable<GenerateDescriptionsCronJobModel>
{
	private static final Logger LOG = LogManager.getLogger(GenerateDescriptionsJob.class);

	static final String QUERY = "SELECT {p:pk} FROM {Product AS p JOIN CatalogVersion AS cv ON {p:catalogVersion}={cv:pk} "
			+ "JOIN Catalog AS c ON {cv:catalog}={c:pk}} WHERE {c:id}=?catalogId AND {cv:version}=?version ORDER BY {p:code}";

	private ProductDescriptionGenerationService productDescriptionGenerationService;
	private FlexibleSearchService flexibleSearchService;
	private ConfigurationService configurationService;

	@Override
	public PerformResult perform(final GenerateDescriptionsCronJobModel cronJob)
	{
		final String version = configurationService.getConfiguration().getString("ai.description.staged.version", "Staged");
		final boolean overwrite = Boolean.TRUE.equals(cronJob.getOverwrite());
		final int batchSize = cronJob.getBatchSize() == null || cronJob.getBatchSize() < 1 ? 20 : cronJob.getBatchSize();
		final int max = cronJob.getMaxProducts() == null ? 0 : cronJob.getMaxProducts();

		int success = 0;
		int skipped = 0;
		int failed = 0;
		int attempted = 0;
		int offset = 0;

		while (max == 0 || attempted < max)
		{
			final FlexibleSearchQuery query = new FlexibleSearchQuery(QUERY);
			query.addQueryParameter("catalogId", cronJob.getCatalogId());
			query.addQueryParameter("version", version);
			query.setStart(offset);
			query.setCount(batchSize);

			final List<ProductModel> batch = flexibleSearchService.<ProductModel> search(query).getResult();
			if (batch.isEmpty())
			{
				break;
			}
			offset += batch.size();

			for (final ProductModel product : batch)
			{
				if (clearAbortRequestedIfNeeded(cronJob))
				{
					LOG.info("Description job aborted: success={}, skipped={}, failed={}", success, skipped, failed);
					return new PerformResult(CronJobResult.ERROR, CronJobStatus.ABORTED);
				}
				if (max != 0 && attempted >= max)
				{
					break;
				}

				final GenerationResult result = productDescriptionGenerationService.generateAndSave(product, overwrite);
				switch (result.getStatus())
				{
					case SUCCESS:
						success++;
						attempted++;
						break;
					case FAILED:
						failed++;
						attempted++;
						break;
					default:
						skipped++;
						break;
				}
			}
		}

		LOG.info("Description job finished: success={}, skipped={}, failed={}", success, skipped, failed);
		return new PerformResult(failed > 0 && success == 0 ? CronJobResult.ERROR : CronJobResult.SUCCESS,
				CronJobStatus.FINISHED);
	}

	@Override
	public boolean isAbortable()
	{
		return true;
	}

	public void setProductDescriptionGenerationService(final ProductDescriptionGenerationService service)
	{
		this.productDescriptionGenerationService = service;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}
}

