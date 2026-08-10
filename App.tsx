import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {
  ActivityIndicator,
  Alert,
  AppState,
  FlatList,
  Image,
  Modal,
  Pressable,
  RefreshControl,
  SafeAreaView,
  ScrollView,
  StatusBar,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  View,
} from 'react-native';
import {
  NotificationAppInfo,
  NotificationData,
  NotificationPage,
  NotificationQuery,
  getNotification,
  getNotificationApps,
  getNotifications,
  getUnreadNotificationCount,
  exportNotifications,
  getSystemNotificationsEnabled,
  isIgnoringBatteryOptimizations,
  isNotificationListenerConnected,
  isNotificationListenerEnabled,
  openBatteryOptimizationSettings,
  openNotificationListenerSettings,
  searchNotifications,
  setAppNotificationsEnabled,
  setNotificationRead,
  setSystemNotificationsEnabled,
  subscribeToNotificationLifecycle,
} from './src/NotificationListener';

const PAGE_SIZE = 40;
type Tab = 'dashboard' | 'notifications' | 'apps' | 'settings';
type DateRange = 'all' | 'today' | '7d' | '30d';

function dayKey(timestamp: number) {
  const d = new Date(timestamp);
  return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
}

function dateLabel(timestamp: number) {
  const d = new Date(timestamp);
  const today = new Date();
  if (dayKey(timestamp) === dayKey(today.getTime())) return 'Today';
  const yesterday = new Date(today);
  yesterday.setDate(today.getDate() - 1);
  if (dayKey(timestamp) === dayKey(yesterday.getTime())) return 'Yesterday';
  return d.toLocaleDateString([], {weekday: 'short', day: 'numeric', month: 'short', year: d.getFullYear() === today.getFullYear() ? undefined : 'numeric'});
}

function timeLabel(timestamp: number) {
  return new Date(timestamp).toLocaleTimeString([], {hour: 'numeric', minute: '2-digit'});
}

function rangeBounds(range: DateRange): {fromTime: number; toTime: number} {
  const now = Date.now();
  if (range === 'all') return {fromTime: 0, toTime: 0};
  const start = new Date();
  start.setHours(0, 0, 0, 0);
  if (range === '7d') start.setDate(start.getDate() - 6);
  if (range === '30d') start.setDate(start.getDate() - 29);
  return {fromTime: start.getTime(), toTime: now};
}

function initials(name: string) {
  const clean = name.trim();
  return clean ? clean.slice(0, 1).toUpperCase() : '?';
}

function Icon({app, size = 42}: {app?: NotificationAppInfo; size?: number}) {
  if (app?.iconBase64) {
    return <Image source={{uri: `data:image/png;base64,${app.iconBase64}`}} style={{width: size, height: size, borderRadius: size * 0.22}} />;
  }
  return (
    <View style={[styles.iconFallback, {width: size, height: size, borderRadius: size * 0.22}]}>
      <Text style={styles.iconFallbackText}>{initials(app?.appName ?? '')}</Text>
    </View>
  );
}

function StatusPill({item}: {item: NotificationData}) {
  return (
    <View style={[styles.pill, item.isActive ? styles.pillActive : styles.pillMuted]}>
      <Text style={styles.pillText}>{item.isActive ? 'Active' : 'Removed'}</Text>
    </View>
  );
}

export default function App() {
  const [tab, setTab] = useState<Tab>('dashboard');
  const [notifications, setNotifications] = useState<NotificationData[]>([]);
  const [total, setTotal] = useState(0);
  const [unread, setUnread] = useState(0);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [exporting, setExporting] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [apps, setApps] = useState<NotificationAppInfo[]>([]);
  const [search, setSearch] = useState('');
  const [range, setRange] = useState<DateRange>('all');
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [activeOnly, setActiveOnly] = useState(false);
  const [selectedPackage, setSelectedPackage] = useState('');
  const [systemEnabled, setSystemEnabled] = useState(true);
  const [listenerEnabled, setListenerEnabled] = useState(false);
  const [listenerConnected, setListenerConnected] = useState(false);
  const [batteryOptimized, setBatteryOptimized] = useState(true);
  const [selected, setSelected] = useState<NotificationData | null>(null);
  const [detailVisible, setDetailVisible] = useState(false);

  const offsetRef = useRef(0);
  const loadingMoreRef = useRef(false);
  const mountedRef = useRef(true);
  const searchTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const appMap = useMemo(() => {
    const map = new Map<string, NotificationAppInfo>();
    apps.forEach(app => map.set(app.packageName, app));
    return map;
  }, [apps]);

  const query = useMemo<NotificationQuery>(() => {
    const bounds = rangeBounds(range);
    return {search, packageName: selectedPackage, unreadOnly, activeOnly, ...bounds};
  }, [search, selectedPackage, unreadOnly, activeOnly, range]);

  const selectionMode = selectedIds.size > 0;

  const runExport = useCallback(async (format: 'csv' | 'xlsx' | 'json', ids: string[] = []) => {
    try {
      setExporting(true);
      const fileName = await exportNotifications(format, query, ids);
      setSelectedIds(new Set());
      Alert.alert('Export ready', `${fileName} was created. The Android share/save sheet is open.`);
    } catch (error) {
      console.error('Export failed', error);
      Alert.alert('Export failed', 'The notifications could not be exported. Please try again.');
    } finally {
      setExporting(false);
    }
  }, [query]);

  const chooseExportFormat = useCallback((ids: string[] = []) => {
    if (exporting) return;
    Alert.alert(
      ids.length ? `Export ${ids.length} selected` : 'Export notifications',
      ids.length ? 'Choose a file format.' : 'Export all notifications matching the current filters.',
      [
        {text: 'CSV', onPress: () => runExport('csv', ids)},
        {text: 'Excel (.xlsx)', onPress: () => runExport('xlsx', ids)},
        {text: 'JSON', onPress: () => runExport('json', ids)},
        {text: 'Cancel', style: 'cancel'},
      ],
    );
  }, [exporting, runExport]);

  const toggleSelection = useCallback((id: string) => {
    setSelectedIds(current => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }, []);

  const loadStatus = useCallback(async () => {
    try {
      const [enabled, connected, battery] = await Promise.all([
        isNotificationListenerEnabled(),
        isNotificationListenerConnected(),
        isIgnoringBatteryOptimizations(),
      ]);
      if (!mountedRef.current) return;
      setListenerEnabled(enabled);
      setListenerConnected(connected);
      setBatteryOptimized(!battery);
    } catch (e) { console.error(e); }
  }, []);

  const loadApps = useCallback(async () => {
    try {
      const [items, system] = await Promise.all([getNotificationApps(), getSystemNotificationsEnabled()]);
      if (!mountedRef.current) return;
      setApps(items);
      setSystemEnabled(system);
    } catch (e) { console.error(e); }
  }, []);

  const loadPage = useCallback(async (mode: 'initial' | 'refresh' | 'more' = 'initial') => {
    if (mode === 'more') {
      if (loadingMoreRef.current || !hasMore) return;
      loadingMoreRef.current = true;
      setLoadingMore(true);
    } else if (mode === 'refresh') setRefreshing(true);
    else setLoading(true);

    const offset = mode === 'more' ? offsetRef.current : 0;
    try {
      const page: NotificationPage = search || selectedPackage || unreadOnly || activeOnly || range !== 'all'
        ? await searchNotifications(PAGE_SIZE, offset, query)
        : await getNotifications(PAGE_SIZE, offset);
      if (!mountedRef.current) return;
      setNotifications(current => mode === 'more' ? [...current, ...page.items] : page.items);
      offsetRef.current = page.offset + page.items.length;
      setTotal(page.total);
      setHasMore(page.hasMore);
      const unreadCount = await getUnreadNotificationCount();
      if (mountedRef.current) setUnread(unreadCount);
    } catch (e) { console.error('Failed to load notifications', e); }
    finally {
      if (mode === 'more') { loadingMoreRef.current = false; setLoadingMore(false); }
      else if (mode === 'refresh') setRefreshing(false);
      else setLoading(false);
    }
  }, [activeOnly, hasMore, query, range, search, selectedPackage, unreadOnly]);

  useEffect(() => {
    mountedRef.current = true;
    loadPage('initial');
    loadApps();
    loadStatus();
    const appState = AppState.addEventListener('change', state => {
      if (state === 'active') { loadStatus(); loadApps(); loadPage('refresh'); }
    });
    const sub = subscribeToNotificationLifecycle(async event => {
      if (!mountedRef.current) return;
      const filtering = search || selectedPackage || unreadOnly || activeOnly || range !== 'all';
      if (filtering) {
        loadPage('refresh');
        return;
      }
      setNotifications(current => {
        if (event.eventType === 'removed' || event.eventType === 'updated') {
          return current.map(item => item.id === event.id ? event : item);
        }
        return current.some(item => item.id === event.id) ? current : [event, ...current].slice(0, PAGE_SIZE);
      });
      if (event.eventType === 'posted') {
        setTotal(value => value + 1);
        setUnread(value => value + 1);
      }
    });
    return () => { mountedRef.current = false; appState.remove(); sub.remove(); if (searchTimer.current) clearTimeout(searchTimer.current); };
  }, []);

  useEffect(() => {
    setSelectedIds(new Set());
  }, [search, selectedPackage, unreadOnly, activeOnly, range]);

  useEffect(() => {
    if (searchTimer.current) clearTimeout(searchTimer.current);
    searchTimer.current = setTimeout(() => {
      offsetRef.current = 0;
      loadPage('refresh');
    }, 250);
    return () => { if (searchTimer.current) clearTimeout(searchTimer.current); };
  }, [search, selectedPackage, unreadOnly, activeOnly, range]);

  const openDetail = async (item: NotificationData) => {
    try {
      const fresh = await getNotification(item.id);
      const value = fresh ?? item;
      setSelected(value);
      setDetailVisible(true);
      if (!value.isRead) {
        await setNotificationRead(value.id, true);
        setUnread(v => Math.max(0, v - 1));
        setNotifications(current => current.map(n => n.id === value.id ? {...n, isRead: true} : n));
      }
    } catch (e) { console.error(e); }
  };

  const refresh = () => { offsetRef.current = 0; loadPage('refresh'); };

  const toggleApp = async (app: NotificationAppInfo) => {
    const enabled = !app.enabled;
    await setAppNotificationsEnabled(app.packageName, enabled);
    setApps(items => items.map(item => item.packageName === app.packageName ? {...item, enabled, explicitlyConfigured: true} : item));
  };

  const todayCount = useMemo(() => {
    const key = dayKey(Date.now());
    return notifications.filter(n => dayKey(n.timestamp) === key).length;
  }, [notifications]);

  const groupedNotifications = useMemo(() => {
    const groups: Array<{type: 'header'; key: string; title: string} | {type: 'item'; key: string; item: NotificationData}> = [];
    let last = '';
    notifications.forEach(item => {
      const key = dayKey(item.timestamp);
      if (key !== last) { groups.push({type: 'header', key: `h-${key}`, title: dateLabel(item.timestamp)}); last = key; }
      groups.push({type: 'item', key: item.id, item});
    });
    return groups;
  }, [notifications]);

  const renderItem = ({item}: {item: {type: string; key: string; title?: string; item?: NotificationData}}) => {
    if (item.type === 'header') return <Text style={styles.dateHeader}>{item.title}</Text>;
    const notification = item.item!;
    const app = appMap.get(notification.packageName);
    return (
      <Pressable onPress={() => selectionMode ? toggleSelection(notification.id) : openDetail(notification)} onLongPress={() => toggleSelection(notification.id)} delayLongPress={350} style={({pressed}) => [styles.card, pressed && styles.cardPressed, !notification.isRead && styles.unreadCard, selectedIds.has(notification.id) && styles.selectedCard]}>
        <View style={styles.cardTop}>
          <Icon app={app} size={42} />
          <View style={styles.cardIdentity}>
            <Text style={styles.appName} numberOfLines={1}>{app?.appName ?? notification.packageName}</Text>
            <Text style={styles.time}>{timeLabel(notification.timestamp)}</Text>
          </View>
          <StatusPill item={notification} />
          {selectionMode && <View style={[styles.selectionDot, selectedIds.has(notification.id) && styles.selectionDotActive]}><Text style={styles.selectionDotText}>{selectedIds.has(notification.id) ? '✓' : ''}</Text></View>}
        </View>
        {!!notification.title && <Text style={styles.notificationTitle} numberOfLines={2}>{notification.title}</Text>}
        {!!notification.text && <Text style={styles.notificationText} numberOfLines={4}>{notification.text}</Text>}
        <View style={styles.metaRow}>
          {!!notification.category && <Text style={styles.meta}>{notification.category}</Text>}
          {notification.isOngoing && <Text style={styles.meta}>Ongoing</Text>}
          {notification.groupKey && <Text style={styles.meta}>Grouped</Text>}
          {!notification.isRead && <Text style={styles.unreadMeta}>Unread</Text>}
        </View>
      </Pressable>
    );
  };

  const renderNotifications = () => (
    <View style={styles.flex}>
      <View style={styles.searchBox}>
        <Text style={styles.searchIcon}>⌕</Text>
        <TextInput value={search} onChangeText={setSearch} placeholder="Search notifications" placeholderTextColor="#8c8c93" style={styles.searchInput} returnKeyType="search" />
        {search.length > 0 && <Pressable onPress={() => setSearch('')}><Text style={styles.clearText}>×</Text></Pressable>}
      </View>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chips}>
        {(['all', 'today', '7d', '30d'] as DateRange[]).map(value => <Pressable key={value} onPress={() => setRange(value)} style={[styles.chip, range === value && styles.chipActive]}><Text style={[styles.chipText, range === value && styles.chipTextActive]}>{value === 'all' ? 'All time' : value === 'today' ? 'Today' : value === '7d' ? '7 days' : '30 days'}</Text></Pressable>)}
        <Pressable onPress={() => setUnreadOnly(v => !v)} style={[styles.chip, unreadOnly && styles.chipActive]}><Text style={[styles.chipText, unreadOnly && styles.chipTextActive]}>Unread</Text></Pressable>
        <Pressable onPress={() => setActiveOnly(v => !v)} style={[styles.chip, activeOnly && styles.chipActive]}><Text style={[styles.chipText, activeOnly && styles.chipTextActive]}>Active</Text></Pressable>
      </ScrollView>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.appChips}>
        <Pressable onPress={() => setSelectedPackage('')} style={[styles.appChip, !selectedPackage && styles.appChipActive]}><Text style={[styles.appChipText, !selectedPackage && styles.appChipTextActive]}>All apps</Text></Pressable>
        {apps.filter(a => a.enabled).map(app => <Pressable key={app.packageName} onPress={() => setSelectedPackage(app.packageName)} style={[styles.appChip, selectedPackage === app.packageName && styles.appChipActive]}><Icon app={app} size={22}/><Text style={[styles.appChipText, selectedPackage === app.packageName && styles.appChipTextActive]} numberOfLines={1}>{app.appName}</Text></Pressable>)}
      </ScrollView>
      <View style={styles.exportToolbar}>
        {selectionMode ? (
          <>
            <Text style={styles.selectionText}>{selectedIds.size} selected</Text>
            <Pressable style={styles.secondarySmallButton} onPress={() => chooseExportFormat(Array.from(selectedIds))} disabled={exporting}><Text style={styles.secondaryButtonText}>{exporting ? 'Exporting…' : 'Export selected'}</Text></Pressable>
            <Pressable style={styles.textButton} onPress={() => setSelectedIds(new Set())}><Text style={styles.textButtonText}>Cancel</Text></Pressable>
          </>
        ) : (
          <>
            <Text style={styles.exportHint}>Long-press to select notifications</Text>
            <Pressable style={styles.secondarySmallButton} onPress={() => chooseExportFormat()} disabled={exporting}><Text style={styles.secondaryButtonText}>{exporting ? 'Exporting…' : 'Export'}</Text></Pressable>
          </>
        )}
      </View>
      {loading ? <View style={styles.center}><ActivityIndicator size="large"/><Text style={styles.muted}>Loading history…</Text></View> : (
        <FlatList
          data={groupedNotifications}
          keyExtractor={item => item.key}
          renderItem={renderItem}
          contentContainerStyle={groupedNotifications.length ? styles.list : styles.emptyList}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh}/>} 
          onEndReached={() => { if (hasMore && !loadingMore) loadPage('more'); }}
          onEndReachedThreshold={0.3}
          ListEmptyComponent={<View style={styles.emptyBox}><Text style={styles.emptyTitle}>No matching notifications</Text><Text style={styles.muted}>Try another search, date range, or app filter.</Text></View>}
          ListFooterComponent={loadingMore ? <View style={styles.footer}><ActivityIndicator/><Text style={styles.muted}>Loading older notifications…</Text></View> : null}
        />
      )}
    </View>
  );

  const renderDashboard = () => (
    <ScrollView contentContainerStyle={styles.dashboard} refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh}/>}> 
      <View style={styles.hero}><Text style={styles.heroEyebrow}>LOCAL NOTIFICATION VAULT</Text><Text style={styles.heroTitle}>Everything important, kept on your device.</Text><Text style={styles.heroText}>No account, no cloud database, and no backend. Your notification history stays local.</Text></View>
      <View style={styles.statsGrid}>
        <View style={styles.statCard}><Text style={styles.statValue}>{total.toLocaleString()}</Text><Text style={styles.statLabel}>Total</Text></View>
        <View style={styles.statCard}><Text style={styles.statValue}>{unread.toLocaleString()}</Text><Text style={styles.statLabel}>Unread</Text></View>
        <View style={styles.statCard}><Text style={styles.statValue}>{todayCount}</Text><Text style={styles.statLabel}>Loaded today</Text></View>
        <View style={styles.statCard}><Text style={styles.statValue}>{apps.length}</Text><Text style={styles.statLabel}>Apps</Text></View>
      </View>
      <View style={styles.panel}><View style={styles.panelTitleRow}><Text style={styles.panelTitle}>Service health</Text><View style={[styles.healthDot, listenerConnected && styles.healthDotGood]}/></View><Text style={styles.healthLine}>Access: {listenerEnabled ? 'Enabled' : 'Not enabled'}</Text><Text style={styles.healthLine}>Listener: {listenerConnected ? 'Connected' : 'Disconnected'}</Text><Text style={styles.healthLine}>Battery: {batteryOptimized ? 'Optimization may restrict background work' : 'Not restricted'}</Text><Pressable style={styles.primaryButton} onPress={() => setTab(listenerEnabled ? 'notifications' : 'settings')}><Text style={styles.primaryButtonText}>{listenerEnabled ? 'View notifications' : 'Enable access'}</Text></Pressable></View>
      <Pressable style={styles.panel} onPress={() => setTab('notifications')}><Text style={styles.panelTitle}>Recent notifications</Text><Text style={styles.panelSubtitle}>Browse, search, filter, and open any notification for full details.</Text><Text style={styles.link}>Open notification history →</Text></Pressable>
    </ScrollView>
  );

  const renderApps = () => (
    <FlatList data={apps} keyExtractor={item => item.packageName} contentContainerStyle={styles.appList} ListHeaderComponent={<><Text style={styles.screenTitle}>Apps</Text><Text style={styles.screenSubtitle}>Choose which apps are allowed to save new notifications.</Text><View style={styles.systemToggle}><View><Text style={styles.appName}>System notifications</Text><Text style={styles.packageText}>Android and system applications</Text></View><Switch value={systemEnabled} onValueChange={async value => {await setSystemNotificationsEnabled(value); setSystemEnabled(value);}}/></View></>} renderItem={({item}) => <View style={styles.appRow}><Icon app={item} size={44}/><View style={styles.appText}><Text style={styles.appName}>{item.appName}</Text><Text style={styles.packageText}>{item.packageName}</Text></View><Switch value={item.enabled} onValueChange={() => toggleApp(item)}/></View>}/>
  );

  const renderSettings = () => (
    <ScrollView contentContainerStyle={styles.settings}><Text style={styles.screenTitle}>Settings</Text><Text style={styles.screenSubtitle}>Control notification access and background reliability.</Text><View style={styles.panel}><Text style={styles.panelTitle}>Notification access</Text><Text style={styles.healthLine}>Access: {listenerEnabled ? 'Enabled' : 'Not enabled'}</Text><Text style={styles.healthLine}>Service: {listenerConnected ? 'Connected' : 'Disconnected'}</Text><Pressable style={styles.primaryButton} onPress={openNotificationListenerSettings}><Text style={styles.primaryButtonText}>{listenerEnabled ? 'Open notification access settings' : 'Enable notification access'}</Text></Pressable></View><View style={styles.panel}><Text style={styles.panelTitle}>Battery optimization</Text><Text style={styles.panelSubtitle}>{batteryOptimized ? 'Your device may restrict background work.' : 'The app is not currently restricted by battery optimization.'}</Text><Pressable style={styles.secondaryButton} onPress={openBatteryOptimizationSettings}><Text style={styles.secondaryButtonText}>Review battery optimization</Text></Pressable></View><View style={styles.panel}><Text style={styles.panelTitle}>Export & backup</Text><Text style={styles.panelSubtitle}>Export your complete notification history as CSV, Excel, or JSON. Files stay local until you choose where to save or share them.</Text><View style={styles.exportButtonRow}><Pressable style={styles.secondarySmallButton} onPress={() => runExport('csv')} disabled={exporting}><Text style={styles.secondaryButtonText}>CSV</Text></Pressable><Pressable style={styles.secondarySmallButton} onPress={() => runExport('xlsx')} disabled={exporting}><Text style={styles.secondaryButtonText}>Excel</Text></Pressable><Pressable style={styles.secondarySmallButton} onPress={() => runExport('json')} disabled={exporting}><Text style={styles.secondaryButtonText}>JSON</Text></Pressable></View></View><View style={styles.panel}><Text style={styles.panelTitle}>Storage</Text><Text style={styles.panelSubtitle}>Notification history is stored locally in the Room database on this device. Nothing is uploaded to a server.</Text></View></ScrollView>
  );

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="dark-content" backgroundColor="#f7f7f8" />
      <View style={styles.topBar}><View><Text style={styles.brand}>Notification Puller</Text><Text style={styles.topCount}>{unread ? `${unread} unread` : 'All caught up'}</Text></View><View style={styles.connection}><View style={[styles.healthDot, listenerConnected && styles.healthDotGood]}/><Text style={styles.connectionText}>{listenerConnected ? 'Live' : 'Offline'}</Text></View></View>
      <View style={styles.content}>{tab === 'dashboard' ? renderDashboard() : tab === 'notifications' ? renderNotifications() : tab === 'apps' ? renderApps() : renderSettings()}</View>
      <View style={styles.bottomNav}>{([['dashboard','Home'],['notifications','History'],['apps','Apps'],['settings','Settings']] as [Tab,string][]).map(([value,label]) => <Pressable key={value} onPress={() => setTab(value)} style={styles.navItem}><Text style={[styles.navIcon, tab === value && styles.navActive]}>{value === 'dashboard' ? '⌂' : value === 'notifications' ? '▤' : value === 'apps' ? '◉' : '⚙'}</Text><Text style={[styles.navLabel, tab === value && styles.navActive]}>{label}</Text></Pressable>)}</View>

      <Modal visible={detailVisible} animationType="slide" transparent onRequestClose={() => setDetailVisible(false)}>
        <View style={styles.modalBackdrop}><View style={styles.detailSheet}>{selected && <ScrollView><View style={styles.detailHandle}/><View style={styles.detailHeader}><Icon app={appMap.get(selected.packageName)} size={56}/><View style={styles.detailIdentity}><Text style={styles.detailApp}>{appMap.get(selected.packageName)?.appName ?? selected.packageName}</Text><Text style={styles.detailPackage}>{selected.packageName}</Text></View><Pressable onPress={() => setDetailVisible(false)}><Text style={styles.close}>×</Text></Pressable></View><StatusPill item={selected}/><Text style={styles.detailTitle}>{selected.title || 'Untitled notification'}</Text><Text style={styles.detailText}>{selected.text || selected.subText || 'No notification text'}</Text><View style={styles.detailInfo}><Text style={styles.detailLabel}>Posted</Text><Text style={styles.detailValue}>{new Date(selected.timestamp).toLocaleString()}</Text><Text style={styles.detailLabel}>Status</Text><Text style={styles.detailValue}>{selected.status}</Text><Text style={styles.detailLabel}>Read</Text><Text style={styles.detailValue}>{selected.isRead ? 'Yes' : 'No'}</Text>{selected.removedAt ? <><Text style={styles.detailLabel}>Removed</Text><Text style={styles.detailValue}>{new Date(selected.removedAt).toLocaleString()}</Text></> : null}<Text style={styles.detailLabel}>Category</Text><Text style={styles.detailValue}>{selected.category || '—'}</Text><Text style={styles.detailLabel}>Notification key</Text><Text style={styles.detailValue}>{selected.notificationKey}</Text></View></ScrollView>}</View></View>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container:{flex:1,backgroundColor:'#f7f7f8'}, flex:{flex:1}, content:{flex:1},
  topBar:{paddingHorizontal:20,paddingTop:14,paddingBottom:12,flexDirection:'row',alignItems:'center',justifyContent:'space-between',backgroundColor:'#f7f7f8'},
  brand:{fontSize:23,fontWeight:'800',color:'#171719'}, topCount:{fontSize:12,color:'#777',marginTop:3},
  connection:{flexDirection:'row',alignItems:'center',gap:6,paddingHorizontal:10,paddingVertical:7,borderRadius:20,backgroundColor:'#fff'}, connectionText:{fontSize:12,fontWeight:'700',color:'#555'},
  healthDot:{width:8,height:8,borderRadius:4,backgroundColor:'#c4c4c8'},healthDotGood:{backgroundColor:'#18a558'},
  dashboard:{padding:16,paddingBottom:30}, hero:{padding:22,borderRadius:22,backgroundColor:'#19191c',marginBottom:14},heroEyebrow:{fontSize:10,fontWeight:'800',letterSpacing:1.2,color:'#aaa'},heroTitle:{fontSize:25,fontWeight:'800',color:'#fff',marginTop:8,lineHeight:30},heroText:{fontSize:13,color:'#c8c8cc',lineHeight:19,marginTop:8},
  statsGrid:{flexDirection:'row',flexWrap:'wrap',gap:10,marginBottom:14},statCard:{width:'48%',backgroundColor:'#fff',borderRadius:16,padding:16},statValue:{fontSize:24,fontWeight:'800',color:'#161619'},statLabel:{fontSize:12,color:'#777',marginTop:3},
  panel:{backgroundColor:'#fff',borderRadius:18,padding:17,marginBottom:12},panelTitleRow:{flexDirection:'row',justifyContent:'space-between',alignItems:'center'},panelTitle:{fontSize:16,fontWeight:'800',color:'#1b1b1e'},panelSubtitle:{fontSize:13,color:'#777',lineHeight:19,marginTop:5},healthLine:{fontSize:13,color:'#555',marginTop:8},link:{fontSize:13,fontWeight:'700',marginTop:14,color:'#222'},
  primaryButton:{marginTop:14,backgroundColor:'#1b1b1e',borderRadius:12,paddingVertical:12,alignItems:'center'},primaryButtonText:{color:'#fff',fontWeight:'700'},
  secondaryButton:{marginTop:12,borderWidth:1,borderColor:'#ddd',borderRadius:12,paddingVertical:11,alignItems:'center'},secondaryButtonText:{color:'#333',fontWeight:'700'},
  searchBox:{marginHorizontal:15,marginTop:4,backgroundColor:'#fff',borderRadius:14,paddingHorizontal:13,flexDirection:'row',alignItems:'center',borderWidth:1,borderColor:'#e5e5e7'},searchIcon:{fontSize:24,color:'#777',marginRight:7},searchInput:{flex:1,height:45,fontSize:15,color:'#222'},clearText:{fontSize:24,color:'#888',padding:4},
  chips:{paddingHorizontal:15,paddingVertical:10,gap:8},chip:{paddingHorizontal:13,paddingVertical:8,borderRadius:20,backgroundColor:'#fff',borderWidth:1,borderColor:'#e3e3e5'},chipActive:{backgroundColor:'#1b1b1e',borderColor:'#1b1b1e'},chipText:{fontSize:12,fontWeight:'600',color:'#555'},chipTextActive:{color:'#fff'},appChips:{paddingHorizontal:15,paddingBottom:8,gap:7},appChip:{flexDirection:'row',alignItems:'center',gap:6,paddingRight:10,paddingLeft:4,paddingVertical:4,borderRadius:18,backgroundColor:'#fff',borderWidth:1,borderColor:'#e3e3e5'},appChipActive:{borderColor:'#1b1b1e'},appChipText:{fontSize:11,color:'#555',maxWidth:100},appChipTextActive:{fontWeight:'700',color:'#222'},
  exportToolbar:{marginHorizontal:15,marginBottom:4,minHeight:44,flexDirection:'row',alignItems:'center',justifyContent:'space-between',gap:8},exportHint:{flex:1,fontSize:11,color:'#888'},selectionText:{flex:1,fontSize:12,fontWeight:'800',color:'#333'},secondarySmallButton:{borderWidth:1,borderColor:'#ddd',borderRadius:11,paddingHorizontal:12,paddingVertical:9,backgroundColor:'#fff'},textButton:{paddingHorizontal:8,paddingVertical:9},textButtonText:{fontSize:12,fontWeight:'700',color:'#555'},exportButtonRow:{flexDirection:'row',gap:8,marginTop:12},selectedCard:{borderColor:'#777',backgroundColor:'#f2f2f3'},selectionDot:{width:22,height:22,borderRadius:11,borderWidth:1,borderColor:'#ccc',alignItems:'center',justifyContent:'center',marginLeft:7},selectionDotActive:{backgroundColor:'#1b1b1e',borderColor:'#1b1b1e'},selectionDotText:{fontSize:12,fontWeight:'800',color:'#fff'},list:{paddingHorizontal:15,paddingBottom:30},emptyList:{flexGrow:1,padding:20},dateHeader:{fontSize:13,fontWeight:'800',color:'#777',marginTop:12,marginBottom:4,paddingHorizontal:3},card:{backgroundColor:'#fff',borderRadius:17,padding:14,marginVertical:5,borderWidth:1,borderColor:'#ececee'},cardPressed:{opacity:.75},unreadCard:{borderColor:'#cfcfd3',borderLeftWidth:3},cardTop:{flexDirection:'row',alignItems:'center'},cardIdentity:{flex:1,marginLeft:10},appName:{fontSize:14,fontWeight:'700',color:'#222'},time:{fontSize:11,color:'#999',marginTop:2},iconFallback:{backgroundColor:'#ededf0',alignItems:'center',justifyContent:'center'},iconFallbackText:{fontSize:16,fontWeight:'800',color:'#555'},pill:{paddingHorizontal:8,paddingVertical:4,borderRadius:20},pillActive:{backgroundColor:'#e8f6ed'},pillMuted:{backgroundColor:'#eee'},pillText:{fontSize:9,fontWeight:'800',color:'#555',textTransform:'uppercase'},notificationTitle:{fontSize:16,fontWeight:'800',color:'#171719',marginTop:12},notificationText:{fontSize:14,color:'#454549',lineHeight:20,marginTop:4},metaRow:{flexDirection:'row',gap:9,marginTop:10,flexWrap:'wrap'},meta:{fontSize:10,color:'#888'},unreadMeta:{fontSize:10,color:'#222',fontWeight:'800'},footer:{padding:20,alignItems:'center',gap:8},center:{flex:1,alignItems:'center',justifyContent:'center',gap:10},muted:{fontSize:13,color:'#888',textAlign:'center'},emptyBox:{alignItems:'center',paddingTop:90},emptyTitle:{fontSize:18,fontWeight:'800',color:'#333',marginBottom:7},
  appList:{padding:16,paddingBottom:30},screenTitle:{fontSize:25,fontWeight:'800',color:'#171719'},screenSubtitle:{fontSize:13,color:'#777',lineHeight:19,marginTop:4,marginBottom:14},systemToggle:{backgroundColor:'#fff',borderRadius:16,padding:14,flexDirection:'row',alignItems:'center',justifyContent:'space-between',marginBottom:10},appRow:{backgroundColor:'#fff',borderRadius:16,padding:12,marginVertical:4,flexDirection:'row',alignItems:'center'},appText:{flex:1,marginLeft:11},packageText:{fontSize:10,color:'#888',marginTop:3},settings:{padding:16,paddingBottom:30},
  bottomNav:{height:68,borderTopWidth:1,borderTopColor:'#e7e7e9',backgroundColor:'#fff',flexDirection:'row',justifyContent:'space-around',alignItems:'center'},navItem:{alignItems:'center',justifyContent:'center',minWidth:70},navIcon:{fontSize:20,color:'#999'},navLabel:{fontSize:10,color:'#888',marginTop:2,fontWeight:'600'},navActive:{color:'#171719',fontWeight:'800'},
  modalBackdrop:{flex:1,backgroundColor:'rgba(0,0,0,.45)',justifyContent:'flex-end'},detailSheet:{maxHeight:'88%',backgroundColor:'#f7f7f8',borderTopLeftRadius:25,borderTopRightRadius:25,padding:18},detailHandle:{width:42,height:4,borderRadius:2,backgroundColor:'#ccc',alignSelf:'center',marginBottom:18},detailHeader:{flexDirection:'row',alignItems:'center'},detailIdentity:{flex:1,marginLeft:12},detailApp:{fontSize:17,fontWeight:'800',color:'#222'},detailPackage:{fontSize:11,color:'#888',marginTop:2},close:{fontSize:30,color:'#777',padding:5},detailTitle:{fontSize:21,fontWeight:'800',color:'#171719',marginTop:18},detailText:{fontSize:16,color:'#444',lineHeight:24,marginTop:8},detailInfo:{backgroundColor:'#fff',borderRadius:16,padding:15,marginTop:20,marginBottom:25},detailLabel:{fontSize:10,fontWeight:'800',textTransform:'uppercase',color:'#999',marginTop:10},detailValue:{fontSize:13,color:'#333',marginTop:3},
});
