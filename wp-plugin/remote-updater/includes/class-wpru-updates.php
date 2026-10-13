<?php
/**
 * Lecture et application des mises à jour (cœur, extensions, thèmes, traductions)
 * via les classes d'upgrader natives de WordPress.
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

class WPRU_Updates {

	private static function load_admin() {
		require_once ABSPATH . 'wp-admin/includes/file.php';
		require_once ABSPATH . 'wp-admin/includes/misc.php';
		require_once ABSPATH . 'wp-admin/includes/plugin.php';
		require_once ABSPATH . 'wp-admin/includes/theme.php';
		require_once ABSPATH . 'wp-admin/includes/update.php';
		require_once ABSPATH . 'wp-admin/includes/translation-install.php';
		require_once ABSPATH . 'wp-admin/includes/class-wp-upgrader.php';
	}

	private static function prepare_long_task() {
		if ( function_exists( 'set_time_limit' ) ) {
			@set_time_limit( 300 ); // phpcs:ignore WordPress.PHP.NoSilencedErrors
		}
		if ( function_exists( 'wp_raise_memory_limit' ) ) {
			wp_raise_memory_limit( 'admin' );
		}
	}

	/**
	 * Force WordPress à interroger wordpress.org (et les sources tierces) pour les mises à jour.
	 */
	public static function refresh() {
		self::load_admin();
		delete_site_transient( 'update_core' );
		delete_site_transient( 'update_plugins' );
		delete_site_transient( 'update_themes' );
		wp_version_check( array(), true );
		wp_update_plugins();
		wp_update_themes();
	}

	public static function site_info() {
		self::load_admin();
		global $wp_version;

		$counts = wp_get_update_data();

		return array(
			'name'           => is_multisite() ? get_network_option( null, 'site_name' ) : get_bloginfo( 'name' ),
			'url'            => network_home_url( '/' ),
			'admin_url'      => network_admin_url(),
			'icon_url'       => self::site_icon(),
			'wp_version'     => $wp_version,
			'php_version'    => PHP_VERSION,
			'multisite'      => is_multisite(),
			'sites_count'    => is_multisite() ? (int) get_blog_count() : 1,
			'plugin_version' => WPRU_VERSION,
			'file_mods'      => wp_is_file_mod_allowed( 'remote_updater' ),
			'fs_method'      => get_filesystem_method(),
			'counts'         => array(
				'core'         => (int) $counts['counts']['wordpress'],
				'plugins'      => (int) $counts['counts']['plugins'],
				'themes'       => (int) $counts['counts']['themes'],
				'translations' => (int) $counts['counts']['translations'],
				'total'        => (int) $counts['counts']['total'],
			),
			'last_checked'   => self::last_checked(),
		);
	}

	/**
	 * Icône du site (Personnaliser › Identité du site) ; en multisite, celle du site principal.
	 * Chaîne vide si aucune icône n'est définie (l'app essaiera alors /favicon.ico).
	 */
	private static function site_icon() {
		$blog_id = is_multisite() ? get_main_site_id() : null;
		$url     = get_site_icon_url( 192, '', $blog_id );
		return $url ? esc_url_raw( $url ) : '';
	}

	private static function last_checked() {
		$t = get_site_transient( 'update_plugins' );
		return ( is_object( $t ) && isset( $t->last_checked ) ) ? (int) $t->last_checked : 0;
	}

	public static function core() {
		self::load_admin();
		global $wp_version;

		$updates = get_core_updates( array( 'dismissed' => true ) );
		$latest  = null;
		if ( is_array( $updates ) ) {
			foreach ( $updates as $u ) {
				if ( isset( $u->response ) && 'upgrade' === $u->response ) {
					$latest = $u;
					break;
				}
			}
		}

		return array(
			'current'          => $wp_version,
			'update_available' => (bool) $latest,
			'new_version'      => $latest ? $latest->current : null,
			'locale'           => $latest ? $latest->locale : get_locale(),
			'auto_update'      => self::core_auto_update_mode(),
		);
	}

	private static function core_auto_update_mode() {
		if ( defined( 'WP_AUTO_UPDATE_CORE' ) ) {
			return is_bool( WP_AUTO_UPDATE_CORE ) ? ( WP_AUTO_UPDATE_CORE ? 'all' : 'off' ) : (string) WP_AUTO_UPDATE_CORE;
		}
		return get_site_option( 'auto_update_core_major' ) === 'enabled' ? 'all' : 'minor';
	}

	public static function plugins() {
		self::load_admin();

		$all       = get_plugins();
		$transient = get_site_transient( 'update_plugins' );
		$auto      = (array) get_site_option( 'auto_update_plugins', array() );
		$auto_ok   = wp_is_auto_update_enabled_for_type( 'plugin' );
		$list      = array();

		foreach ( $all as $file => $data ) {
			$update = ( is_object( $transient ) && isset( $transient->response[ $file ] ) ) ? $transient->response[ $file ] : null;

			$list[] = array(
				'file'             => $file,
				'name'             => wp_strip_all_tags( $data['Name'] ),
				'version'          => $data['Version'],
				'author'           => wp_strip_all_tags( $data['AuthorName'] ? $data['AuthorName'] : $data['Author'] ),
				'active'           => is_multisite() ? is_plugin_active_for_network( $file ) : is_plugin_active( $file ),
				'network'          => ! empty( $data['Network'] ),
				'update_available' => (bool) $update,
				'new_version'      => $update ? $update->new_version : null,
				'package'          => $update ? ! empty( $update->package ) : null,
				'requires_wp'      => $update && isset( $update->requires ) ? $update->requires : null,
				'requires_php'     => $update && isset( $update->requires_php ) ? $update->requires_php : null,
				'tested'           => $update && isset( $update->tested ) ? $update->tested : null,
				'auto_update'      => $auto_ok && in_array( $file, $auto, true ),
				'self'             => plugin_basename( WPRU_FILE ) === $file,
			);
		}

		usort( $list, array( __CLASS__, 'sort_updates_first' ) );
		return $list;
	}

	public static function themes() {
		self::load_admin();

		$transient = get_site_transient( 'update_themes' );
		$auto      = (array) get_site_option( 'auto_update_themes', array() );
		$auto_ok   = wp_is_auto_update_enabled_for_type( 'theme' );
		$active    = get_stylesheet();
		$list      = array();

		foreach ( wp_get_themes() as $slug => $theme ) {
			$update = ( is_object( $transient ) && isset( $transient->response[ $slug ] ) ) ? $transient->response[ $slug ] : null;

			$list[] = array(
				'slug'             => $slug,
				'name'             => wp_strip_all_tags( $theme->get( 'Name' ) ),
				'version'          => $theme->get( 'Version' ),
				'author'           => wp_strip_all_tags( $theme->get( 'Author' ) ),
				'active'           => $slug === $active,
				'parent'           => $theme->parent() ? $theme->get_template() : null,
				'update_available' => (bool) $update,
				'new_version'      => $update ? $update['new_version'] : null,
				'package'          => $update ? ! empty( $update['package'] ) : null,
				'requires_wp'      => $update && isset( $update['requires'] ) ? $update['requires'] : null,
				'requires_php'     => $update && isset( $update['requires_php'] ) ? $update['requires_php'] : null,
				'auto_update'      => $auto_ok && in_array( $slug, $auto, true ),
			);
		}

		usort( $list, array( __CLASS__, 'sort_updates_first' ) );
		return $list;
	}

	public static function translations() {
		self::load_admin();
		$list = array();
		foreach ( wp_get_translation_updates() as $t ) {
			$list[] = array(
				'type'     => $t->type,
				'slug'     => $t->slug,
				'language' => $t->language,
				'version'  => $t->version,
			);
		}
		return $list;
	}

	private static function sort_updates_first( $a, $b ) {
		if ( $a['update_available'] !== $b['update_available'] ) {
			return $a['update_available'] ? -1 : 1;
		}
		return strcasecmp( $a['name'], $b['name'] );
	}

	/* ---------------------------------------------------------------------
	 * Application des mises à jour
	 * ------------------------------------------------------------------ */

	private static function check_file_mods() {
		if ( ! wp_is_file_mod_allowed( 'remote_updater' ) ) {
			return new WP_Error( 'wpru_file_mods_disabled', 'Les modifications de fichiers sont désactivées sur ce site (DISALLOW_FILE_MODS).', array( 'status' => 409 ) );
		}
		return true;
	}

	private static function skin_errors( $skin ) {
		$errors = $skin->get_errors();
		return $errors->has_errors() ? $errors->get_error_messages() : array();
	}

	/**
	 * @param string[] $files Fichiers d'extensions (ex. "akismet/akismet.php"). Vide = toutes.
	 */
	public static function update_plugins( array $files ) {
		self::load_admin();
		$check = self::check_file_mods();
		if ( is_wp_error( $check ) ) {
			return $check;
		}
		if ( ! current_user_can( 'update_plugins' ) ) {
			return new WP_Error( 'wpru_forbidden', 'Droits insuffisants pour mettre à jour les extensions.', array( 'status' => 403 ) );
		}

		self::prepare_long_task();
		wp_update_plugins();
		$transient = get_site_transient( 'update_plugins' );
		$available = is_object( $transient ) && ! empty( $transient->response ) ? array_keys( $transient->response ) : array();

		$files   = empty( $files ) ? $available : array_values( array_intersect( array_map( 'sanitize_text_field', $files ), $available ) );
		$self    = plugin_basename( WPRU_FILE );
		$results = array();

		if ( empty( $files ) ) {
			return array( 'results' => array(), 'message' => 'Aucune mise à jour d\'extension disponible.' );
		}

		// On met à jour notre propre extension en dernier pour ne pas couper la requête.
		usort(
			$files,
			function ( $a, $b ) use ( $self ) {
				return ( $a === $self ) - ( $b === $self );
			}
		);

		$was_active = array();
		foreach ( $files as $file ) {
			$was_active[ $file ] = is_multisite() && is_plugin_active_for_network( $file ) ? 'network' : ( is_plugin_active( $file ) ? 'site' : false );
		}

		$skin     = new WP_Ajax_Upgrader_Skin();
		$upgrader = new Plugin_Upgrader( $skin );
		$raw      = $upgrader->bulk_upgrade( $files );

		$all = get_plugins();
		foreach ( $files as $file ) {
			$r  = is_array( $raw ) && array_key_exists( $file, $raw ) ? $raw[ $file ] : null;
			$ok = is_array( $r ) || true === $r;

			// Réactive l'extension si la mise à jour l'a désactivée.
			if ( $ok && $was_active[ $file ] && ! is_plugin_active( $file ) ) {
				activate_plugin( $file, '', 'network' === $was_active[ $file ], true );
			}

			$results[] = array(
				'file'    => $file,
				'name'    => isset( $all[ $file ] ) ? wp_strip_all_tags( $all[ $file ]['Name'] ) : $file,
				'success' => $ok,
				'version' => isset( $all[ $file ] ) ? $all[ $file ]['Version'] : null,
				'error'   => $ok ? null : ( is_wp_error( $r ) ? $r->get_error_message() : 'Échec de la mise à jour.' ),
			);
		}

		wp_clean_plugins_cache( true );
		wp_update_plugins();

		return array(
			'results' => $results,
			'errors'  => self::skin_errors( $skin ),
		);
	}

	/**
	 * @param string[] $slugs Slugs de thèmes. Vide = tous.
	 */
	public static function update_themes( array $slugs ) {
		self::load_admin();
		$check = self::check_file_mods();
		if ( is_wp_error( $check ) ) {
			return $check;
		}
		if ( ! current_user_can( 'update_themes' ) ) {
			return new WP_Error( 'wpru_forbidden', 'Droits insuffisants pour mettre à jour les thèmes.', array( 'status' => 403 ) );
		}

		self::prepare_long_task();
		wp_update_themes();
		$transient = get_site_transient( 'update_themes' );
		$available = is_object( $transient ) && ! empty( $transient->response ) ? array_keys( $transient->response ) : array();
		$slugs     = empty( $slugs ) ? $available : array_values( array_intersect( array_map( 'sanitize_text_field', $slugs ), $available ) );

		if ( empty( $slugs ) ) {
			return array( 'results' => array(), 'message' => 'Aucune mise à jour de thème disponible.' );
		}

		$skin     = new WP_Ajax_Upgrader_Skin();
		$upgrader = new Theme_Upgrader( $skin );
		$raw      = $upgrader->bulk_upgrade( $slugs );
		$results  = array();

		foreach ( $slugs as $slug ) {
			$r     = is_array( $raw ) && array_key_exists( $slug, $raw ) ? $raw[ $slug ] : null;
			$ok    = is_array( $r ) || true === $r;
			$theme = wp_get_theme( $slug );

			$results[] = array(
				'slug'    => $slug,
				'name'    => $theme->exists() ? wp_strip_all_tags( $theme->get( 'Name' ) ) : $slug,
				'success' => $ok,
				'version' => $theme->exists() ? $theme->get( 'Version' ) : null,
				'error'   => $ok ? null : ( is_wp_error( $r ) ? $r->get_error_message() : 'Échec de la mise à jour.' ),
			);
		}

		wp_clean_themes_cache( true );
		wp_update_themes();

		return array(
			'results' => $results,
			'errors'  => self::skin_errors( $skin ),
		);
	}

	public static function update_core() {
		self::load_admin();
		$check = self::check_file_mods();
		if ( is_wp_error( $check ) ) {
			return $check;
		}
		if ( ! current_user_can( 'update_core' ) ) {
			return new WP_Error( 'wpru_forbidden', 'Droits insuffisants pour mettre à jour WordPress.', array( 'status' => 403 ) );
		}

		self::prepare_long_task();
		wp_version_check( array(), true );

		$update = null;
		foreach ( (array) get_core_updates( array( 'dismissed' => true ) ) as $u ) {
			if ( isset( $u->response ) && 'upgrade' === $u->response ) {
				$update = $u;
				break;
			}
		}

		if ( ! $update ) {
			return array(
				'success' => true,
				'message' => 'WordPress est déjà à jour.',
				'version' => get_bloginfo( 'version' ),
			);
		}

		$skin     = new WP_Ajax_Upgrader_Skin();
		$upgrader = new Core_Upgrader( $skin );
		$result   = $upgrader->upgrade( $update, array( 'allow_relaxed_file_ownership' => true ) );

		if ( is_wp_error( $result ) ) {
			return new WP_Error( 'wpru_core_failed', $result->get_error_message(), array( 'status' => 500, 'errors' => self::skin_errors( $skin ) ) );
		}
		if ( ! $result ) {
			return new WP_Error( 'wpru_core_failed', 'La mise à jour de WordPress a échoué.', array( 'status' => 500, 'errors' => self::skin_errors( $skin ) ) );
		}

		// Mise à jour éventuelle de la base de données (comme après une mise à jour manuelle).
		require_once ABSPATH . 'wp-admin/includes/upgrade.php';
		if ( function_exists( 'wp_upgrade' ) ) {
			wp_upgrade();
		}
		delete_site_transient( 'update_core' );

		return array(
			'success' => true,
			'message' => 'WordPress a été mis à jour.',
			'version' => is_string( $result ) ? $result : $update->current,
		);
	}

	public static function update_translations() {
		self::load_admin();
		$check = self::check_file_mods();
		if ( is_wp_error( $check ) ) {
			return $check;
		}
		if ( ! current_user_can( 'update_languages' ) && ! current_user_can( 'update_core' ) ) {
			return new WP_Error( 'wpru_forbidden', 'Droits insuffisants pour mettre à jour les traductions.', array( 'status' => 403 ) );
		}

		self::prepare_long_task();
		$updates = wp_get_translation_updates();
		if ( empty( $updates ) ) {
			return array( 'success' => true, 'message' => 'Les traductions sont à jour.', 'count' => 0 );
		}

		$skin     = new WP_Ajax_Upgrader_Skin();
		$upgrader = new Language_Pack_Upgrader( $skin );
		$result   = $upgrader->bulk_upgrade( $updates );

		return array(
			'success' => ! is_wp_error( $result ) && false !== $result,
			'message' => is_wp_error( $result ) ? $result->get_error_message() : 'Traductions mises à jour.',
			'count'   => count( $updates ),
			'errors'  => self::skin_errors( $skin ),
		);
	}

	/**
	 * Mise à jour manuelle : installe un .zip envoyé depuis l'application en
	 * remplaçant la version existante (équivalent de « Téléverser une extension »
	 * puis « Remplacer la version installée »).
	 *
	 * @param string $type 'plugin' ou 'theme'.
	 * @param array  $file Entrée de $_FILES.
	 */
	public static function install_package( $type, $file ) {
		self::load_admin();
		$check = self::check_file_mods();
		if ( is_wp_error( $check ) ) {
			return $check;
		}

		$caps = 'plugin' === $type ? array( 'upload_plugins', 'update_plugins' ) : array( 'upload_themes', 'update_themes' );
		foreach ( $caps as $cap ) {
			if ( ! current_user_can( $cap ) ) {
				return new WP_Error( 'wpru_forbidden', 'Droits insuffisants pour téléverser ce fichier.', array( 'status' => 403 ) );
			}
		}

		if ( empty( $file ) || ! isset( $file['tmp_name'] ) ) {
			return new WP_Error(
				'wpru_no_file',
				sprintf( 'Aucun fichier reçu. Le fichier dépasse peut-être la limite du serveur (%s).', size_format( wp_max_upload_size() ) ),
				array( 'status' => 400 )
			);
		}
		if ( ! empty( $file['error'] ) ) {
			return new WP_Error( 'wpru_upload_error', 'Erreur lors de l\'envoi du fichier (code ' . (int) $file['error'] . ').', array( 'status' => 400 ) );
		}
		if ( ! is_uploaded_file( $file['tmp_name'] ) ) {
			return new WP_Error( 'wpru_upload_error', 'Fichier invalide.', array( 'status' => 400 ) );
		}
		if ( 'zip' !== strtolower( pathinfo( $file['name'], PATHINFO_EXTENSION ) ) ) {
			return new WP_Error( 'wpru_not_zip', 'Le fichier doit être une archive .zip.', array( 'status' => 400 ) );
		}

		self::prepare_long_task();

		// Copie dans un fichier temporaire WordPress (supprimé après installation).
		$package = wp_tempnam( $file['name'] );
		if ( ! $package || ! move_uploaded_file( $file['tmp_name'], $package ) ) {
			return new WP_Error( 'wpru_upload_error', 'Impossible d\'enregistrer le fichier sur le serveur.', array( 'status' => 500 ) );
		}

		$skin = new WP_Ajax_Upgrader_Skin();

		if ( 'plugin' === $type ) {
			$before   = get_plugins();
			$upgrader = new Plugin_Upgrader( $skin );
			$result   = $upgrader->install( $package, array( 'overwrite_package' => true ) );
		} else {
			$upgrader = new Theme_Upgrader( $skin );
			$result   = $upgrader->install( $package, array( 'overwrite_package' => true ) );
		}

		if ( file_exists( $package ) ) {
			wp_delete_file( $package );
		}

		if ( is_wp_error( $result ) || ! $result ) {
			$errors  = self::skin_errors( $skin );
			$message = is_wp_error( $result ) ? $result->get_error_message() : ( $errors ? implode( ' ', $errors ) : 'L\'installation a échoué.' );
			return new WP_Error( 'wpru_install_failed', wp_strip_all_tags( $message ), array( 'status' => 500 ) );
		}

		if ( 'plugin' === $type ) {
			wp_clean_plugins_cache( true );
			$plugin_file = $upgrader->plugin_info();
			$after       = get_plugins();
			$data        = $plugin_file && isset( $after[ $plugin_file ] ) ? $after[ $plugin_file ] : null;
			$old         = $plugin_file && isset( $before[ $plugin_file ] ) ? $before[ $plugin_file ]['Version'] : null;
			wp_update_plugins();

			return array(
				'type'        => 'plugin',
				'id'          => $plugin_file,
				'name'        => $data ? wp_strip_all_tags( $data['Name'] ) : basename( $file['name'], '.zip' ),
				'old_version' => $old,
				'version'     => $data ? $data['Version'] : null,
				'active'      => $plugin_file ? is_plugin_active( $plugin_file ) : false,
				'message'     => $old ? 'Extension remplacée.' : 'Extension installée (non activée).',
			);
		}

		wp_clean_themes_cache( true );
		$theme = $upgrader->theme_info();
		wp_update_themes();

		return array(
			'type'        => 'theme',
			'id'          => $theme ? $theme->get_stylesheet() : null,
			'name'        => $theme ? wp_strip_all_tags( $theme->get( 'Name' ) ) : basename( $file['name'], '.zip' ),
			'old_version' => null,
			'version'     => $theme ? $theme->get( 'Version' ) : null,
			'active'      => $theme ? get_stylesheet() === $theme->get_stylesheet() : false,
			'message'     => 'Thème installé.',
		);
	}

	/**
	 * Active / désactive la mise à jour automatique d'une extension ou d'un thème.
	 */
	public static function set_auto_update( $type, $item, $enabled ) {
		self::load_admin();

		$option = 'plugin' === $type ? 'auto_update_plugins' : 'auto_update_themes';
		$cap    = 'plugin' === $type ? 'update_plugins' : 'update_themes';

		if ( ! current_user_can( $cap ) ) {
			return new WP_Error( 'wpru_forbidden', 'Droits insuffisants.', array( 'status' => 403 ) );
		}
		if ( ! wp_is_auto_update_enabled_for_type( $type ) ) {
			return new WP_Error( 'wpru_auto_disabled', 'Les mises à jour automatiques sont désactivées sur ce site.', array( 'status' => 409 ) );
		}

		$exists = 'plugin' === $type ? array_key_exists( $item, get_plugins() ) : wp_get_theme( $item )->exists();
		if ( ! $exists ) {
			return new WP_Error( 'wpru_not_found', 'Élément introuvable.', array( 'status' => 404 ) );
		}

		$list = (array) get_site_option( $option, array() );
		$list = $enabled ? array_unique( array_merge( $list, array( $item ) ) ) : array_diff( $list, array( $item ) );
		update_site_option( $option, array_values( $list ) );

		return array(
			'type'        => $type,
			'item'        => $item,
			'auto_update' => (bool) $enabled,
		);
	}
}
