package io.github.romulomga.corekit.review

import android.app.Activity
import android.util.Log
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.CancellationException

/**
 * A folha de avaliação da Play (In-App Review). Só a Play decide se algo aparece, e ela não conta ao app; sem a Play
 * (aparelho sem serviços, sem rede) o pedido some em silêncio, porque nunca é erro para a pessoa.
 */
public object StoreReview {
    /**
     * `true` se a Play foi chamada até o fim. Marque "já pedi nesta versão" só depois de um `true`: marcar antes
     * queimaria a chance da versão quando o app sai no meio do caminho.
     */
    public suspend fun request(activity: Activity): Boolean = try {
        val manager = ReviewManagerFactory.create(activity)

        manager.launchReview(activity, manager.requestReview())

        true
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.w(TAG, "in-app review unavailable: ${error.javaClass.simpleName}")

        false
    }

    private const val TAG = "CoreKit.review"
}
