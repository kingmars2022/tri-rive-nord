<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useI18n } from "../useI18n";
import { usePreferenceStore } from "../stores/preferences";
import { useMunicipalityStore } from "../stores/municipality";
import { fetchStreets, type MunicipalitySector, type Street } from "../api/municipalities";
import { useAuthStore } from "../stores/auth";
import { api } from "../api/client";
import {
  browserSupportsPush,
  currentSubscription,
  serverPushSettings,
  subscribeToPush,
  unsubscribeFromPush
} from "../api/push";
import type { Language } from "../i18n/messages";
import type { Sector } from "../stores/preferences";

type BackendPreference = {
  sector: Sector;
  languageCode: Language;
  reminderEnabled: boolean;
};

const { t, language } = useI18n();
const preferences = usePreferenceStore();
const municipalities = useMunicipalityStore();

const sectors = computed(() => municipalities.current?.sectors ?? []);

function sectorName(sector: MunicipalitySector) {
  return language.value === "en"
    ? sector.nameEn
    : language.value === "zh"
      ? sector.nameZh
      : sector.nameFr;
}
// Street lookup, for cities where a resident cannot name their sector from
// memory. Boisbriand's sectors are "A, Tuesday" and so on, assigned street
// by street; choosing from the list unaided is a guess, and a wrong guess
// shows another street's calendar.
const hasStreetDirectory = computed(() => municipalities.current?.hasStreetDirectory ?? false);
const streetQuery = ref("");
const streetResults = ref<Street[]>([]);
const streetSearched = ref(false);
const chosenStreet = ref<Street | null>(null);
let streetTimer: ReturnType<typeof setTimeout> | undefined;
let streetRequest = 0;

function sectorNameByCode(code: string) {
  const sector = sectors.value.find((s) => s.code === code);
  return sector ? sectorName(sector) : code;
}

function onStreetInput(event: Event) {
  streetQuery.value = (event.target as HTMLInputElement).value;
  chosenStreet.value = null;
  clearTimeout(streetTimer);
  const query = streetQuery.value.trim();
  if (query.length < 2) {
    streetResults.value = [];
    streetSearched.value = false;
    return;
  }
  streetTimer = setTimeout(() => searchStreets(query), 250);
}

async function searchStreets(query: string) {
  const city = municipalities.current;
  if (!city) return;
  // Only the newest request may write the list: a slow answer to "gr"
  // arriving after the answer to "grande" must not replace it.
  const request = ++streetRequest;
  try {
    const found = await fetchStreets(city.slug, query);
    if (request === streetRequest) {
      streetResults.value = found;
      streetSearched.value = true;
    }
  } catch {
    if (request === streetRequest) {
      streetResults.value = [];
      streetSearched.value = true;
    }
  }
}

function chooseStreet(street: Street) {
  chosenStreet.value = street;
  streetResults.value = [];
  preferences.setSector(street.sectorCode as Sector);
  persistToBackend();
}

const auth = useAuthStore();
const synced = ref(false);

// Push has three independent preconditions - the browser, the resident's
// permission, and a server key - and each one failing means something
// different to the person reading the page.
const pushSupported = ref(browserSupportsPush());
const pushOffered = ref(false);
const pushPublicKey = ref("");
const pushOn = ref(false);
const pushBusy = ref(false);
const pushError = ref("");

onMounted(async () => {
  if (!auth.isAuthenticated) {
    return;
  }

  try {
    const remote = await api.get<BackendPreference>("/preferences");
    preferences.setLanguage(remote.languageCode);
    preferences.setSector(remote.sector);
    preferences.setRemindersEnabled(remote.reminderEnabled);
    synced.value = true;
  } catch {
    synced.value = false;
  }

  await loadPushState();
});

async function loadPushState() {
  if (!pushSupported.value) {
    return;
  }
  try {
    const settings = await serverPushSettings();
    pushOffered.value = settings.enabled && settings.publicKey.length > 0;
    pushPublicKey.value = settings.publicKey;
    pushOn.value = (await currentSubscription()) !== null;
  } catch {
    // Not knowing whether push is available is a reason to hide the control,
    // not to show one that cannot work.
    pushOffered.value = false;
  }
}

async function onPushChange(event: Event) {
  const wanted = (event.target as HTMLInputElement).checked;
  pushBusy.value = true;
  pushError.value = "";
  try {
    if (wanted) {
      await subscribeToPush(pushPublicKey.value, preferences.language);
      pushOn.value = true;
    } else {
      await unsubscribeFromPush();
      pushOn.value = false;
    }
  } catch (err) {
    // A denied permission is the resident's decision, and the browser will not
    // ask again - so it needs its own message rather than "something failed".
    pushError.value = err instanceof Error && err.message === "notification-permission-denied"
      ? t("settings.pushDenied")
      : t("settings.pushFailed");
    pushOn.value = false;
  } finally {
    pushBusy.value = false;
  }
}

async function persistToBackend() {
  if (!auth.isAuthenticated) {
    return;
  }

  await api.put<BackendPreference>("/preferences", {
    sector: preferences.sector,
    languageCode: preferences.language,
    reminderEnabled: preferences.remindersEnabled
  });
  synced.value = true;
}

function onLanguageChange(event: Event) {
  preferences.setLanguage((event.target as HTMLSelectElement).value as Language);
  persistToBackend();
}

function onSectorChange(event: Event) {
  preferences.setSector((event.target as HTMLSelectElement).value as Sector);
  persistToBackend();
}

function onReminderChange(event: Event) {
  preferences.setRemindersEnabled((event.target as HTMLInputElement).checked);
  persistToBackend();
}
</script>

<template>
  <section class="page">
    <h1>{{ t("settings.title") }}</h1>

    <p class="admin-note">
      {{ auth.isAuthenticated ? t("settings.synced") : t("settings.signInToSync") }}
    </p>

    <label>
      {{ t("settings.language") }}
      <select :value="preferences.language" @change="onLanguageChange">
        <option value="fr">Français</option>
        <option value="en">English</option>
        <option value="zh">中文</option>
      </select>
    </label>

    <label>
      {{ t("settings.sector") }}
      <select :value="preferences.sector" @change="onSectorChange">
        <!--
          The options come from the city rather than from the translation
          file. A sector is a local fact, so a city that splits its territory
          some other way -- or not at all -- needs data, not a new string.
        -->
        <option v-for="sector in sectors" :key="sector.code" :value="sector.code">
          {{ sectorName(sector) }}
        </option>
      </select>
    </label>

    <div v-if="hasStreetDirectory" class="street-lookup">
      <label>
        {{ t("settings.streetLookup") }}
        <input
          type="search"
          :value="streetQuery"
          :placeholder="t('settings.streetPlaceholder')"
          autocomplete="off"
          @input="onStreetInput"
        />
      </label>
      <ul v-if="streetResults.length" class="street-results">
        <li v-for="street in streetResults" :key="street.name">
          <button type="button" @click="chooseStreet(street)">
            {{ street.name }} — {{ sectorNameByCode(street.sectorCode) }}
          </button>
        </li>
      </ul>
      <p v-else-if="streetSearched && !chosenStreet" class="admin-note">{{ t("settings.streetNotFound") }}</p>
      <p v-if="chosenStreet" class="admin-note street-chosen">
        {{ t("settings.streetChosen") }} {{ sectorNameByCode(chosenStreet.sectorCode) }}
      </p>
      <!-- Where the table comes from, every time it is used: the city's
           printed street list, checked against its 2026 calendar (V31). -->
      <p class="admin-note">{{ t("settings.streetSource") }}</p>
    </div>

    <label class="checkbox">
      <input
        type="checkbox"
        :checked="preferences.remindersEnabled"
        @change="onReminderChange"
      />
      {{ t("settings.reminders") }}
    </label>

    <!--
      The toggle used to say only "Reminders", beside a stored reminder_time
      that nothing ever read. It has one real effect - whether city notices
      reach this resident's inbox - so it now says that, and says what it does
      not do.
    -->
    <p class="admin-note">{{ t("settings.remindersHelp") }}</p>

    <template v-if="auth.isAuthenticated && pushSupported && pushOffered">
      <label class="checkbox">
        <input
          type="checkbox"
          :checked="pushOn"
          :disabled="pushBusy || !preferences.remindersEnabled"
          @change="onPushChange"
        />
        {{ t("settings.push") }}
      </label>
      <p class="admin-note">{{ t("settings.pushHelp") }}</p>
      <p v-if="pushError" class="auth-error">{{ pushError }}</p>
    </template>
  </section>
</template>
