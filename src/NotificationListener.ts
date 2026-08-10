import {
  NativeEventEmitter,
  NativeModules,
  EmitterSubscription,
} from 'react-native';

export type NotificationLifecycleEvent =
  | 'posted'
  | 'updated'
  | 'removed';

export interface NotificationData {
  id: string;
  notificationKey: string;
  packageName: string;
  title: string;
  text: string;
  subText: string;
  category: string;
  timestamp: number;
  isOngoing: boolean;
  createdAt: number;
  status: NotificationLifecycleEvent;
  updatedAt: number;
  removedAt: number | null;
  isActive: boolean;
  isRead: boolean;
  groupKey: string;
  isGroupSummary: boolean;
}

export interface NotificationEvent extends NotificationData {
  eventType: NotificationLifecycleEvent;
}

export interface NotificationAppInfo {
  packageName: string;
  appName: string;
  isSystemApp: boolean;
  enabled: boolean;
  explicitlyConfigured: boolean;
  iconBase64?: string | null;
}


export interface NotificationQuery {
  search?: string;
  packageName?: string;
  unreadOnly?: boolean;
  activeOnly?: boolean;
  fromTime?: number;
  toTime?: number;
}

export interface NotificationPage {
  items: NotificationData[];
  total: number;
  limit: number;
  offset: number;
  hasMore: boolean;
}

interface NativeNotificationModule {
  getNotifications(limit: number, offset: number): Promise<NotificationPage>;
  searchNotifications(limit: number, offset: number, search: string, packageName: string, unreadOnly: boolean, activeOnly: boolean, fromTime: number, toTime: number): Promise<NotificationPage>;
  getUnreadCount(): Promise<number>;
  setNotificationRead(id: string, isRead: boolean): Promise<boolean>;
  getNotification(id: string): Promise<NotificationData | null>;
  getCount(): Promise<number>;
  saveNotification(
    notification: Omit<NotificationData, 'id' | 'createdAt'> &
      Partial<Pick<NotificationData, 'id' | 'createdAt'>>,
  ): Promise<NotificationData>;
  updateNotification(
    notification: NotificationData,
  ): Promise<NotificationData | null>;
  deleteNotification(id: string): Promise<boolean>;
  clearAll(): Promise<boolean>;
  exportNotifications(
    format: 'csv' | 'xlsx' | 'json',
    search: string,
    packageName: string,
    unreadOnly: boolean,
    activeOnly: boolean,
    fromTime: number,
    toTime: number,
    ids: string[],
  ): Promise<string>;
  getNotificationApps(): Promise<NotificationAppInfo[]>;
  setAppNotificationsEnabled(packageName: string, enabled: boolean): Promise<boolean>;
  clearAppNotificationOverride(packageName: string): Promise<boolean>;
  setSystemNotificationsEnabled(enabled: boolean): Promise<boolean>;
  getSystemNotificationsEnabled(): Promise<boolean>;
  isNotificationListenerEnabled(): Promise<boolean>;
  isNotificationListenerConnected(): Promise<boolean>;
  getNotificationListenerLastStateChange(): Promise<number>;
  openNotificationListenerSettings(): Promise<boolean>;
  isIgnoringBatteryOptimizations(): Promise<boolean>;
  openBatteryOptimizationSettings(): Promise<boolean>;
}

const {NotificationModule} = NativeModules as {
  NotificationModule: NativeNotificationModule;
};

if (!NotificationModule) {
  throw new Error(
    'NotificationModule is not available. Run the app on Android with the native module installed.',
  );
}

const notificationEmitter = new NativeEventEmitter(
  NativeModules.NotificationModule,
);

export function subscribeToNotificationLifecycle(
  callback: (event: NotificationEvent) => void,
): EmitterSubscription {
  return notificationEmitter.addListener(
    'notificationLifecycle',
    callback,
  );
}

/**
 * Backward-compatible alias. New code should use
 * subscribeToNotificationLifecycle().
 */
export const subscribeToNotifications = subscribeToNotificationLifecycle;

export async function getNotifications(
  limit = 50,
  offset = 0,
): Promise<NotificationPage> {
  return NotificationModule.getNotifications(limit, offset);
}

export async function searchNotifications(
  limit = 50,
  offset = 0,
  query: NotificationQuery = {},
): Promise<NotificationPage> {
  return NotificationModule.searchNotifications(
    limit,
    offset,
    query.search ?? '',
    query.packageName ?? '',
    query.unreadOnly ?? false,
    query.activeOnly ?? false,
    query.fromTime ?? 0,
    query.toTime ?? 0,
  );
}

export async function getUnreadNotificationCount(): Promise<number> {
  return NotificationModule.getUnreadCount();
}

export async function setNotificationRead(
  id: string,
  isRead = true,
): Promise<boolean> {
  return NotificationModule.setNotificationRead(id, isRead);
}

export async function getNotification(
  id: string,
): Promise<NotificationData | null> {
  return NotificationModule.getNotification(id);
}

export async function getNotificationCount(): Promise<number> {
  return NotificationModule.getCount();
}

export async function saveNotification(
  notification: Omit<NotificationData, 'id' | 'createdAt'> &
    Partial<Pick<NotificationData, 'id' | 'createdAt'>>,
): Promise<NotificationData> {
  return NotificationModule.saveNotification(notification);
}

export async function updateNotification(
  notification: NotificationData,
): Promise<NotificationData | null> {
  return NotificationModule.updateNotification(notification);
}

export async function deleteNotification(id: string): Promise<boolean> {
  return NotificationModule.deleteNotification(id);
}

export async function clearAllNotifications(): Promise<boolean> {
  return NotificationModule.clearAll();
}

export type NotificationExportFormat = 'csv' | 'xlsx' | 'json';

export async function exportNotifications(
  format: NotificationExportFormat,
  query: NotificationQuery = {},
  ids: string[] = [],
): Promise<string> {
  return NotificationModule.exportNotifications(
    format,
    query.search ?? '',
    query.packageName ?? '',
    query.unreadOnly ?? false,
    query.activeOnly ?? false,
    query.fromTime ?? 0,
    query.toTime ?? 0,
    ids,
  );
}

export async function getNotificationApps(): Promise<NotificationAppInfo[]> {
  return NotificationModule.getNotificationApps();
}

export async function setAppNotificationsEnabled(
  packageName: string,
  enabled: boolean,
): Promise<boolean> {
  return NotificationModule.setAppNotificationsEnabled(packageName, enabled);
}

export async function clearAppNotificationOverride(
  packageName: string,
): Promise<boolean> {
  return NotificationModule.clearAppNotificationOverride(packageName);
}

export async function setSystemNotificationsEnabled(
  enabled: boolean,
): Promise<boolean> {
  return NotificationModule.setSystemNotificationsEnabled(enabled);
}

export async function getSystemNotificationsEnabled(): Promise<boolean> {
  return NotificationModule.getSystemNotificationsEnabled();
}

export async function isNotificationListenerEnabled(): Promise<boolean> {
  return NotificationModule.isNotificationListenerEnabled();
}

export async function isNotificationListenerConnected(): Promise<boolean> {
  return NotificationModule.isNotificationListenerConnected();
}

export async function getNotificationListenerLastStateChange(): Promise<number> {
  return NotificationModule.getNotificationListenerLastStateChange();
}

export async function openNotificationListenerSettings(): Promise<boolean> {
  return NotificationModule.openNotificationListenerSettings();
}

export async function isIgnoringBatteryOptimizations(): Promise<boolean> {
  return NotificationModule.isIgnoringBatteryOptimizations();
}

export async function openBatteryOptimizationSettings(): Promise<boolean> {
  return NotificationModule.openBatteryOptimizationSettings();
}
