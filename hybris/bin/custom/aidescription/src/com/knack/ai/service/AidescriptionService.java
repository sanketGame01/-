/*
 * Copyright (c) 2021 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.knack.ai.service;

public interface AidescriptionService
{
	String getHybrisLogoUrl(String logoCode);

	void createLogo(String logoCode);
}
