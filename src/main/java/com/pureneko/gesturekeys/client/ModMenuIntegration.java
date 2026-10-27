package com.pureneko.gesturekeys.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Optional integration; only loaded by Mod Menu when it is installed. */
public final class ModMenuIntegration implements ModMenuApi {
  @Override
  public ConfigScreenFactory<?> getModConfigScreenFactory() {
    return SettingsScreen::new;
  }
}
