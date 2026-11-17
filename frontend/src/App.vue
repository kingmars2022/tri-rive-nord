<script setup lang="ts">
import { computed, onMounted } from "vue";
import { RouterLink, RouterView, useRouter } from "vue-router";
import { useI18n } from "./useI18n";
import { useAuthStore } from "./stores/auth";
import { useMunicipalityStore } from "./stores/municipality";
import { usePreferenceStore } from "./stores/preferences";

const { t } = useI18n();
const auth = useAuthStore();
const router = useRouter();
const municipalities = useMunicipalityStore();
const preferences = usePreferenceStore();

onMounted(() => {
  // Failing to load the list must not blank the application: the pages below
  // fall back to the stored slug and report their own errors.
  municipalities.load().catch(() => undefined);
});

const chosen = computed(() => municipalities.current?.slug ?? preferences.municipality);

function onCityChange(event: Event) {
  municipalities.select((event.target as HTMLSelectElement).value);
}

function onLogout() {
  auth.logout();
  router.push("/");
}
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <div class="brandline">
        <strong>{{ t("app.name") }}</strong>

      <!--
        Which city you are looking at changes what every date and every bin
        on the page means, so it sits with the wordmark rather than in
        settings. Hidden until the list arrives, so it never flashes a single
        placeholder option.
      -->
      <div v-if="municipalities.all.length > 1" class="city-switch">
        <label for="city">{{ t("city.label") }}</label>
        <select id="city" :value="chosen" @change="onCityChange">
          <option v-for="city in municipalities.all" :key="city.slug" :value="city.slug">
            {{ city.name }}
          </option>
        </select>
      </div>
      </div>

      <nav>
        <RouterLink to="/">{{ t("nav.home") }}</RouterLink>
        <RouterLink to="/tri">{{ t("nav.sorting") }}</RouterLink>
        <RouterLink to="/parametres">{{ t("nav.settings") }}</RouterLink>
        <RouterLink v-if="auth.isAdmin" to="/admin">{{ t("nav.admin") }}</RouterLink>
        <RouterLink v-if="!auth.isAuthenticated" to="/connexion">{{ t("nav.login") }}</RouterLink>
        <button v-else type="button" class="link-button nav-logout" @click="onLogout">
          {{ t("nav.logout") }}
        </button>
      </nav>
    </header>
    <main>
      <RouterView />
    </main>
  </div>
</template>
