/*
 * Copyright (c) 2021 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.knack.ai.setup;

import static com.knack.ai.constants.AidescriptionConstants.PLATFORM_LOGO_CODE;

import de.hybris.platform.core.initialization.SystemSetup;

import java.io.InputStream;

import com.knack.ai.constants.AidescriptionConstants;
import com.knack.ai.service.AidescriptionService;


@SystemSetup(extension = AidescriptionConstants.EXTENSIONNAME)
public class AidescriptionSystemSetup
{
	private final AidescriptionService aidescriptionService;

	public AidescriptionSystemSetup(final AidescriptionService aidescriptionService)
	{
		this.aidescriptionService = aidescriptionService;
	}

	@SystemSetup(process = SystemSetup.Process.ALL, type = SystemSetup.Type.ESSENTIAL)
	public void createEssentialData()
	{
		aidescriptionService.createLogo(PLATFORM_LOGO_CODE);
	}

	private InputStream getImageStream()
	{
		return AidescriptionSystemSetup.class.getResourceAsStream("/aidescription/sap-hybris-platform.png");
	}
}
