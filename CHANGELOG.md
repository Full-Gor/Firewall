# Firewall App - Changelog

## Version 1.0.0 (04/02/2026)

### Fonctionnalités intégrées

#### Onglet Firewall
- Liste de toutes les apps installées avec switch on/off
- Bouton VPN pour activer/désactiver le firewall
- Persistance des apps bloquées (AsyncStorage)

#### Onglet DNS
- Champ pour ajouter un domaine manuellement
- Bouton pour charger la liste StevenBlack (~50,000 domaines anti-pubs)
- Liste des domaines custom avec bouton supprimer
- Compteur de domaines bloqués
- Whitelist CDN intégrée (YouTube, Play Store, AliExpress, etc.)

#### Onglet Logs
- Affichage des connexions bloquées
- Bouton Rafraîchir
- Bouton Vider les logs
- Auto-refresh toutes les 5 secondes quand VPN actif

#### Onglet Settings
- **Démarrage auto**: Lance le VPN au démarrage du téléphone
- **Apps système**: Affiche Chrome, Gmail, etc. dans la liste
- Section informations (apps, domaines, status VPN)
- Bouton réinitialiser tout

---

### Problèmes connus / Bugs

#### Blocage par app (switches)
- **Problème**: Quand on active le switch d'une app (ex: YouTube, AliExpress), ça bloque TOUT le trafic de l'app, pas seulement les pubs
- **Exemple YouTube**: Switch activé = ni pub, ni vidéo, rien ne charge
- **Exemple AliExpress**: Switch activé = plus aucune image visible
- **Cause**: Le VPN utilise `addAllowedApplication()` qui route tout le trafic de l'app vers le VPN, puis le bloque entièrement

#### Blocage DNS custom
- **Problème**: Les domaines ajoutés manuellement (ex: flemmix.me, nexuserv.duckdns.org) ne sont pas bloqués
- **Cause probable**: Le DnsInterceptor ne recharge pas correctement les règles custom

#### Logs
- **Problème**: Aucun log n'apparaît dans l'onglet Logs malgré le VPN actif
- **Cause probable**: Les logs DNS sont créés mais pas récupérés correctement par React Native

#### VPN sans switch
- **Observation**: VPN activé sans aucun switch = les apps fonctionnent normalement
- YouTube: vidéos OK mais pubs toujours présentes (la liste StevenBlack ne bloque pas les pubs YouTube car elles viennent de googlevideo.com qui est whitelisté)

---

### Ce qui fonctionne

| Fonctionnalité | Status |
|----------------|--------|
| Liste des apps | ✅ OK |
| VPN s'active | ✅ OK |
| Apps système dans settings | ✅ OK |
| Démarrage auto (setting) | ✅ OK |
| Whitelist CDN | ✅ OK (YouTube, AliExpress fonctionnent avec VPN) |

### Ce qui ne fonctionne pas

| Fonctionnalité | Status |
|----------------|--------|
| Switch bloque app individuellement | ❌ Bloque TOUT au lieu de filtrer |
| Domaines DNS custom | ❌ Pas bloqués |
| Logs | ❌ Aucun log affiché |
| Blocage pubs YouTube | ❌ Pubs toujours présentes |

---

### Architecture technique

- **Framework**: React Native + Expo (SDK 52)
- **Module natif**: Java (FirewallModule, FirewallVpnService)
- **Méthode VPN**: Android VpnService avec addAllowedApplication()
- **Blocage DNS**: Interception DNS avec réponse NXDOMAIN

### Fichiers modifiés
- `App.tsx` - Interface React Native complète
- `FirewallModule.java` - Bridge React Native ↔ Java
- `FirewallVpnService.java` - Service VPN Android
- `TunnelBuilder.java` - Configuration du tunnel VPN
- `BlockListManager.java` - Gestion blocklist + whitelist CDN
- `ConnectionLogger.java` - Historique connexions
- `DnsInterceptor.java` - Interception et filtrage DNS
