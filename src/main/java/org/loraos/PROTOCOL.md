# LoRa‑OS Protocol Specificatie (v0.1)

## Doel
Dit document beschrijft het basisprotocol voor een klein, versleuteld LoRa‑mesh‑systeem dat eerst in Java wordt gesimuleerd en later op ESP32‑nodes draait.

---

## 1. Packet‑structuur (binaire frame)

Elke radio‑frame heeft de volgende layout:

| Veld    | Grootte | Type      | Beschrijving                                 |
|---------|---------|-----------|---------------------------------------------|
| srcId   | 1 byte  | uint8     | Node‑ID van afzender                         |
| dstId   | 1 byte  | uint8     | Node‑ID van bestemming (of broadcast)        |
| type    | 1 byte  | uint8     | Message type + flags (zie §2)               |
| ttl     | 1 byte  | uint8     | Remaining hops (Time‑To‑Live)               |
| seq     | 2 bytes | uint16 BE | Sequence‑nummer per srcId                    |
| len     | 1 byte  | uint8     | Lengte van payload in bytes                  |
| payload | len     | bytes     | Versleutelde data                            |

**Endianness:** `seq` is big-endian (network order).

**Max payload:** 255 bytes (limiet van het `len`‑veld).

---

## 2. Message type en flags

Het type‑byte wordt als volgt gebruikt:

- **lagere 4 bits (0–3):** base type
- **bit 4:** NO_FORWARD‑flag
- **bits 5–7:** gereserveerd (nu 0)

### Base types

| Base type | Waarde | Beschrijving                         |
|-----------|--------|-------------------------------------|
| CHAT      | 0x0    | plaintext: UTF-8 tekst na decrypt   |
| CMD       | 0x1    | plaintext: command string           |
| ACK       | 0x2    | plaintext: "ACK <seq>"              |

### Flag(s)

| Flag | Bit | Beschrijving |
|------|-----|-------------|
| FLAG_NO_FORWARD | 4 | Niet doorsturen naar andere nodes |

### Helpers (Java / C++)

```java
int baseType = type & 0x0F;
boolean noForward = (type & 0x10) != 0;
int typeWithNoFwd = baseType | 0x10;
```
### Betekenis NO_FORWARD:
Als NO_FORWARD is gezet, mag een node het packet nooit doorsturen naar andere nodes. Alleen de bestemming verwerkt het (of nodes droppen het).

# 3. Encryptie
### 3.1 Algoritme

- Algoritme: ChaCha20‑Poly1305 AEAD
- Sleutel: 256‑bit (32 bytes) gedeeld geheim tussen alle nodes in dezelfde mesh
- Nonce: 96‑bit (12 bytes), per packet uniek
- Ciphertext: versleutelde payload + 128‑bit Poly1305 tag

```java
Cipher.getInstance("ChaCha20-Poly1305/None/NoPadding");
```

### 3.2 Payload‑encoding

Voor elk packet:
1. Maak de plaintext payload (bijv. UTF‑8 string "Hallo").
2. Genereer een 12‑byte random nonce.
3. Roep AEAD‑encryptie aan met de payload als plaintext.

Concatenate:
```java
payload = [nonce (12 bytes)] || [ciphertext+tag]
```
Dit geheel gaat in het payload‑veld van het packet; len geeft de totale lengte aan.

## 4. Mesh‑gedrag

### 4.1 Duplicate‑filter

Per node:

- Houd een set bij van `(srcId, seq)`:
    - als al aanwezig → packet negeren;
    - anders toevoegen en verwerken.

Voorkomt flood‑loops en dubbele verwerking.

### 4.2 TTL

- Elke forwardende node verlaagt `ttl` met 1.
- Als `ttl == 0` na decrement → packet droppen.

### 4.3 Forwardingregels

Voor elk **niet‑duplicate** packet:

1. **Bestemming is deze node** (`dstId == myId`):
    - decrypt `payload`;
    - bepaal `baseType = type & 0x0F`;
    - afhandelen:
        - `CHAT`: doorgeven aan chat‑applicatie;
        - `CMD`: doorgeven aan command‑handler;
        - `ACK`: markeer bericht als bevestigd (zie §5).

2. **Niet bestemming, wel forwarden** (`ttl > 0` en `NO_FORWARD` niet gezet):
    - maak nieuwe packet‑kopie met `ttl = ttl - 1`;
    - zend opnieuw (mesh‑forward).

3. **NO_FORWARD gezet** (`NO_FORWARD` flag actief):
    - niet doorsturen, alleen loggen/droppen.

4. **TTL op**:
    - packet droppen.

## 5. ACK‑mechanisme

### 5.1 Doel

ACK’s geven betrouwbaarheid: afzender weet dat een bericht is aangekomen en kan retries/route‑kwaliteit bepalen.[web:81]

### 5.2 ACK genereren

Als een node een CHAT of CMD ontvangt met `dstId == myId`:

- Na succesvolle decryptie en verwerking stuurt de node een ACK terug:

srcId = myId
dstId = originele.srcId
type = ACK (0x2, zonder NO_FORWARD)
ttl = 3 (voorbeeld)
seq = originele.seq
payload plaintext = "ACK <seq>"
payload encrypted zoals §3

### 5.3 ACK verwerken

Als een node een ACK‑packet ontvangt:

- decrypt payload (bijv. `"ACK 5"`);
- log `ACK received for seq=<seq> from <srcId>`;
- in uitgebreid systeem: verwijder bijbehorende entry uit pending‑map.

---

## 6. CMD‑laag (scriptable gedrag)

CMD‑berichten zijn plaintext strings na decrypt, gebruikt voor besturing/configuratie:

Voorbeelden:

PING
SET_LOG DEBUG
SET_MAXHOPS 3
SET_INTERVAL 10000

text

Elke node heeft een `handleCommand(msg)` die:

- `PING` → logt en eventueel ACK stuurt;
- `SET_...` → interne config aanpast (logLevel, maxHops, intervals, etc.).

Zo ontstaat een eenvoudige “scriptable mode” via het mesh‑protocol.[web:77][web:81]

---

## 7. Implementatie‑richtlijnen

### 7.1 Java‑simulator

- Classes:
    - `Packet`: binaire representatie van frame.
    - `MeshNode`: implementatie van:
        - crypto (ChaCha20‑Poly1305),
        - duplicate‑filter,
        - forwarding,
        - ACK‑handling,
        - CMD‑handler.
    - `LoRaChannel` + `ChannelRadio`: simuleren radio‑ether met latency, packet loss en meerdere nodes.[web:26][web:29]

### 7.2 ESP32‑implementatie

### C++ struct:

```
struct Packet {
    uint8_t srcId;
    uint8_t dstId;
    uint8_t type;
    uint8_t ttl;
    uint16_t seq; // big-endian on the wire
    uint8_t len;
    uint8_t payload;
};
```
- Zelfde frame‑layout en type‑waarden als hierboven.
- Crypto met ChaCha20‑Poly1305 uit mbedTLS of libsodium:
    - zelfde sleutel,
    - 12‑byte nonce,
    - payload als `[nonce][ciphertext+tag]`.[web:68]
- Mesh‑logica (duplicate‑filter, forwarding, ACKs, NO_FORWARD, CMD) port je 1‑op‑1 van de Java‑versie.

---