package com.yubico.webauthn.data

import java.util.Optional

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.yubico.scala.util.JavaConverters._
import com.yubico.u2f.data.messages.key.util.U2fB64Encoding
import com.yubico.webauthn.util.BinaryUtil
import com.yubico.webauthn.util.WebAuthnCodecs


case class AuthenticatorData(
  @JsonIgnore
  val authData: ArrayBuffer
) {
  private val RpIdHashLength = 32
  private val FlagsLength = 1
  private val CounterLength = 4
  private val FixedLengthPartEndIndex = RpIdHashLength + FlagsLength + CounterLength

  @JsonProperty("authData")
  def authDataBase64: String = U2fB64Encoding.encode(authData.toArray)

  /**
    * The SHA-256 hash of the RP ID associated with the credential.
    */
  @JsonIgnore
  val rpIdHash: ArrayBuffer = authData.take(RpIdHashLength)
  @JsonProperty("rpIdHash") def rpIdHashBase64: String = U2fB64Encoding.encode(rpIdHash.toArray)

  /**
    * The flags byte.
    */
  val flags: AuthenticationDataFlags = AuthenticationDataFlags(authData(32))

  /**
    * The 32-bit unsigned signature counter.
    */
  val signatureCounter: Long = {
    val bytes = authData.drop(RpIdHashLength + FlagsLength).take(CounterLength)
    BinaryUtil.getUint32(bytes) getOrElse {
      throw new IllegalArgumentException(s"Invalid signature counter bytes: ${bytes}")
    }
  }

  /**
    * Attestation data, if present.
    *
    * See ''§5.3.1 Attestation data'' of [[com.yubico.webauthn.VersionInfo]] for details.
    */
  val attestationData: Optional[AttestationData] = optionalParts._1.asJava

  /**
    * Extension-defined authenticator data, if present.
    *
    * See ''§8 WebAuthn Extensions'' of [[com.yubico.webauthn.VersionInfo]] for details.
    */
  val extensions: Optional[JsonNode] = optionalParts._2.asJava

  private lazy val optionalParts: (Option[AttestationData], Option[JsonNode]) =
    if (flags.AT)
      parseAttestationData(authData drop FixedLengthPartEndIndex)
    else if (flags.ED)
      (None, Some(WebAuthnCodecs.cbor.readTree(authData.drop(FixedLengthPartEndIndex).toArray)))
    else
      (None, None)

  private def parseAttestationData(bytes: ArrayBuffer): (Some[AttestationData], Option[JsonNode]) = {

    val credentialIdLengthBytes = bytes.slice(16, 16 + 2)
    val L: Int = BinaryUtil.getUint16(credentialIdLengthBytes) getOrElse {
      throw new IllegalArgumentException(s"Invalid credential ID length bytes: ${credentialIdLengthBytes}")
    }

    val optionalBytes: ArrayBuffer = bytes.drop(16 + 2 + L)

    {
      (
        Some(AttestationData(
          aaguid = bytes.slice(0, 16),
          credentialId = bytes.slice(16 + 2, 16 + 2 + L),
          credentialPublicKey = optionalBytes
        )),
        None
      )
    }

  }

}
