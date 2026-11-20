<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useI18n } from "../useI18n";
import { usePreferenceStore } from "../stores/preferences";
import { useMunicipalityStore } from "../stores/municipality";
import { api } from "../api/client";
import { useAuthStore } from "../stores/auth";
import {
  fetchNotifications,
  markNotificationRead,
  type ResidentNotification
} from "../api/notifications";
import type { MessageKey } from "../i18n/messages";

type BinColor = "brown" | "blue" | "black" | "grey" | "none";
type CollectionType = "organic" | "recycling" | "garbage" | "bulky" | "ecocentre" | "special";

type CollectionEvent = {
  id: number;
  collectionDate: string;
  sector: string;
  collectionType: CollectionType;
  binColor: BinColor;
  noteFr: string | null;
  noteEn: string | null;
  noteZh: string | null;
  sourceUrl: string | null;
};

const { t, language } = useI18n();
const preferences = usePreferenceStore();
const municipalities = useMunicipalityStore();

const auth = useAuthStore();

const notifications = ref<ResidentNotification[]>([]);

/**
 * Notices an administrator published, delivered here by the Kafka consumer.
 * Only for signed-in residents, because a notification with nobody to notify
 * is not a notification.
 */
async function loadNotifications() {
  if (!auth.token) {
    notifications.value = [];
    return;
  }
  try {
    notifications.value = await fetchNotifications();
  } catch {
    // The inbox is an addition to this page, not its purpose. A failure here
    // must not stop a resident seeing their next collection.
    notifications.value = [];
  }
}

async function dismiss(notification: ResidentNotification) {
  try {
    await markNotificationRead(notification.id);
    notifications.value = notifications.value.filter((n) => n.id !== notification.id);
  } catch {
    // Already read elsewhere, most likely. Drop it from the list either way.
    notifications.value = notifications.value.filter((n) => n.id !== notification.id);
  }
}

const unreadNotifications = computed(() =>
  notifications.value.filter((n) => n.readAt === null)
);

function notificationTitle(n: ResidentNotification) {
  return language.value === "en" ? n.titleEn : language.value === "zh" ? n.titleZh : n.titleFr;
}

function notificationBody(n: ResidentNotification) {
  return language.value === "en" ? n.bodyEn : language.value === "zh" ? n.bodyZh : n.bodyFr;
}

const events = ref<CollectionEvent[]>([]);
const loading = ref(true);
const error = ref("");

function toDateKey(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

const todayKey = toDateKey(new Date());
const tomorrowKey = toDateKey(new Date(Date.now() + 24 * 60 * 60 * 1000));

async function loadCollections() {
  loading.value = true;
  error.value = "";
  events.value = [];

  // A city whose calendar has not been read has nothing to ask for, and
  // asking anyway produced the server's own 404 text on screen -- in English,
  // naming a sector, for what is not an error at all. The panel below says
  // what is actually true.
  if (municipalities.loaded && !municipalities.current?.calendarVerified) {
    loading.value = false;
    return;
  }

  try {
    events.value = await api.get<CollectionEvent[]>(
      `/collections/upcoming?sector=${preferences.sector}&days=30`
    );
  } catch (err) {
    error.value = err instanceof Error ? err.message : "Failed to load collections";
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadCollections();
  loadNotifications();
});

// A resident who signs in mid-visit should not have to reload to see their
// notices.
watch(() => auth.token, loadNotifications);
watch(() => preferences.sector, loadCollections);
// Switching city changes every date and every bin below, so the page reloads
// rather than leaving the previous city's schedule on screen under a new name.
watch(() => preferences.municipality, loadCollections);

const city = computed(() => municipalities.current);

/**
 * True when this city is offered but its calendar has not been read from its
 * own published document yet. Kept separate from "no collection this week",
 * because a resident cannot tell those apart from an empty page and the two
 * mean opposite things.
 */
const calendarMissing = computed(() => city.value !== null && !city.value.calendarVerified);

/** The chosen sector's own name, which arrives with the city. */
const sectorName = computed(() => {
  const sector = city.value?.sectors.find((s) => s.code === preferences.sector);
  if (!sector) return preferences.sector;
  return language.value === "en" ? sector.nameEn : language.value === "zh" ? sector.nameZh : sector.nameFr;
});

const nextEvent = computed(() => events.value[0] ?? null);
const upcomingEvents = computed(() => events.value.slice(1, 5));

const isToday = computed(() => nextEvent.value?.collectionDate === todayKey);
const isTomorrow = computed(() => nextEvent.value?.collectionDate === tomorrowKey);

function binLabel(binColor: BinColor) {
  return t(`bin.${binColor}` as MessageKey);
}

function typeLabel(collectionType: CollectionType) {
  return t(`collection.${collectionType}` as MessageKey);
}

function noteFor(event: CollectionEvent) {
  return { fr: event.noteFr, en: event.noteEn, zh: event.noteZh }[language.value];
}

function formatDate(dateKey: string) {
  const date = new Date(`${dateKey}T00:00:00`);
  return date.toLocaleDateString(language.value, { weekday: "long", month: "long", day: "numeric" });
}

/**
 * The short form the schedule column uses.
 *
 * <p>The long one wrapped to two lines in every row, which doubled the height
 * of the list and broke the thing a timetable is for: dates lining up down
 * the page so the eye can run past them.
 */
function formatDateShort(dateKey: string) {
  const date = new Date(`${dateKey}T00:00:00`);
  return date.toLocaleDateString(language.value, { weekday: "short", month: "short", day: "numeric" });
}
</script>

<template>
  <section class="page">
    <!--
      Notices an administrator published, fanned out to this resident's inbox
      by the second Kafka consumer group. Above the schedule because a storm
      delay changes what the schedule below means.
    -->
    <section v-if="unreadNotifications.length" class="notice-inbox">
      <h2>{{ t("notifications.title") }}</h2>
      <article v-for="notification in unreadNotifications" :key="notification.id">
        <div>
          <strong>{{ notificationTitle(notification) }}</strong>
          <p>{{ notificationBody(notification) }}</p>
        </div>
        <button type="button" @click="dismiss(notification)">
          {{ t("notifications.markRead") }}
        </button>
      </article>
    </section>

    <p v-if="loading" class="admin-note">{{ t("home.loading") }}</p>

    <!--
      Ahead of the error branch on purpose. A city with no calendar loaded can
      still have produced a failed request on the way here, and reporting that
      instead of this panel shows a stack of server text where the honest
      answer is "we have not read this city's calendar yet".
    -->
    <section v-else-if="calendarMissing" class="not-loaded">
      <p class="eyebrow">{{ city?.name }}</p>
      <h1>{{ t("city.notLoaded") }}</h1>
      <p>{{ t("city.notLoadedBody") }}</p>
    </section>

    <p v-else-if="error" class="auth-error">{{ error }}</p>

    <template v-else-if="nextEvent">
      <div class="notice" :class="nextEvent.binColor">
        <p class="eyebrow">
          {{ isToday ? t("home.today") : isTomorrow ? t("home.tomorrow") : formatDate(nextEvent.collectionDate) }}
        </p>
        <h1>{{ binLabel(nextEvent.binColor) }}</h1>
        <p>{{ typeLabel(nextEvent.collectionType) }}</p>
        <p>{{ isToday ? t("home.bringBack") : t("home.putOut") }}</p>
        <p v-if="noteFor(nextEvent)">{{ noteFor(nextEvent) }}</p>
      </div>

      <section class="panel" v-if="upcomingEvents.length">
        <h2>{{ t("home.upcoming") }}</h2>
        <div class="bin-list">
          <article
            v-for="event in upcomingEvents"
            :key="event.id"
            class="bin-card"
            :class="event.binColor"
          >
            <span>{{ formatDateShort(event.collectionDate) }}</span>
            <strong>{{ binLabel(event.binColor) }} - {{ typeLabel(event.collectionType) }}</strong>
          </article>
        </div>
      </section>
    </template>

    <div v-else class="notice none">
      <p class="eyebrow">{{ t("home.sectorNote") }}: {{ sectorName }}</p>
      <h1>{{ t("home.noCollection") }}</h1>
    </div>
  </section>
</template>
