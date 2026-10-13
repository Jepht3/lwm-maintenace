<?php
/**
 * Gestion des connexions (clés d'accès) entre le site et l'application.
 *
 * Chaque connexion est liée à un administrateur : les requêtes de l'app
 * s'exécutent avec les droits de cet utilisateur. Seul le hash de la clé
 * est stocké en base, la clé en clair n'est affichée qu'une seule fois.
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

class WPRU_Auth {

	const OPTION        = 'wpru_connections';
	const HEADER        = 'X-WPRU-Key';
	const KEY_PREFIX    = 'wpru_';
	const MAX_FAILURES  = 10;
	const LOCKOUT_SECS  = 900;

	/**
	 * @return array<string, array> Connexions indexées par identifiant.
	 */
	public static function get_connections() {
		$connections = get_site_option( self::OPTION, array() );
		return is_array( $connections ) ? $connections : array();
	}

	/**
	 * Crée une connexion et retourne la clé en clair (à n'afficher qu'une fois).
	 *
	 * @return array{id: string, key: string}
	 */
	public static function create_connection( $name, $user_id ) {
		$id  = wp_generate_password( 12, false );
		$key = self::KEY_PREFIX . $id . '_' . wp_generate_password( 40, false );

		$connections        = self::get_connections();
		$connections[ $id ] = array(
			'name'      => sanitize_text_field( $name ),
			'hash'      => self::hash_key( $key ),
			'user_id'   => (int) $user_id,
			'created'   => time(),
			'last_used' => 0,
			'last_ip'   => '',
		);
		update_site_option( self::OPTION, $connections );

		return array(
			'id'  => $id,
			'key' => $key,
		);
	}

	public static function revoke_connection( $id ) {
		$connections = self::get_connections();
		unset( $connections[ $id ] );
		update_site_option( self::OPTION, $connections );
	}

	private static function hash_key( $key ) {
		return hash_hmac( 'sha256', $key, wp_salt( 'auth' ) );
	}

	/**
	 * Construit le code de connexion (encodé dans le QR code) : URL du site + clé.
	 */
	public static function connection_code( $key ) {
		$payload = wp_json_encode(
			array(
				'v'    => 1,
				'url'  => network_home_url( '/' ),
				'rest' => get_rest_url( is_multisite() ? get_main_site_id() : null, 'wpru/v1/' ),
				'key'  => $key,
				'name' => get_network_option( null, 'site_name', get_bloginfo( 'name' ) ),
			)
		);
		return 'wpru:' . rtrim( strtr( base64_encode( $payload ), '+/', '-_' ), '=' );
	}

	/**
	 * permission_callback des routes REST : vérifie la clé et connecte l'utilisateur lié.
	 *
	 * @return true|WP_Error
	 */
	public static function authenticate( WP_REST_Request $request ) {
		$ip = self::client_ip();

		if ( self::is_locked_out( $ip ) ) {
			return new WP_Error( 'wpru_locked', 'Trop de tentatives échouées. Réessayez plus tard.', array( 'status' => 429 ) );
		}

		$key = $request->get_header( self::HEADER );
		if ( ! $key ) {
			$auth = $request->get_header( 'authorization' );
			if ( $auth && 0 === stripos( $auth, 'Bearer ' ) ) {
				$key = trim( substr( $auth, 7 ) );
			}
		}

		if ( ! $key || 0 !== strpos( $key, self::KEY_PREFIX ) ) {
			self::register_failure( $ip );
			return new WP_Error( 'wpru_unauthorized', 'Clé d\'accès manquante ou invalide.', array( 'status' => 401 ) );
		}

		// Format : wpru_<id>_<secret>.
		$parts       = explode( '_', $key, 3 );
		$id          = isset( $parts[1] ) ? $parts[1] : '';
		$connections = self::get_connections();

		if ( ! isset( $connections[ $id ] ) || ! hash_equals( $connections[ $id ]['hash'], self::hash_key( $key ) ) ) {
			self::register_failure( $ip );
			return new WP_Error( 'wpru_unauthorized', 'Clé d\'accès invalide ou révoquée.', array( 'status' => 401 ) );
		}

		$user = get_user_by( 'id', $connections[ $id ]['user_id'] );
		if ( ! $user ) {
			return new WP_Error( 'wpru_user_missing', 'L\'utilisateur lié à cette connexion n\'existe plus.', array( 'status' => 403 ) );
		}

		wp_set_current_user( $user->ID );

		if ( ! current_user_can( 'update_plugins' ) && ! current_user_can( 'update_core' ) ) {
			return new WP_Error( 'wpru_forbidden', 'L\'utilisateur lié n\'a pas les droits de mise à jour.', array( 'status' => 403 ) );
		}

		// Mise à jour de la date de dernière utilisation (au plus une fois par minute).
		if ( time() - (int) $connections[ $id ]['last_used'] > 60 ) {
			$connections[ $id ]['last_used'] = time();
			$connections[ $id ]['last_ip']   = $ip;
			update_site_option( self::OPTION, $connections );
		}

		return true;
	}

	private static function client_ip() {
		return isset( $_SERVER['REMOTE_ADDR'] ) ? sanitize_text_field( wp_unslash( $_SERVER['REMOTE_ADDR'] ) ) : '';
	}

	private static function failure_key( $ip ) {
		return 'wpru_fail_' . md5( $ip );
	}

	private static function is_locked_out( $ip ) {
		return (int) get_site_transient( self::failure_key( $ip ) ) >= self::MAX_FAILURES;
	}

	private static function register_failure( $ip ) {
		$key = self::failure_key( $ip );
		set_site_transient( $key, (int) get_site_transient( $key ) + 1, self::LOCKOUT_SECS );
	}
}
