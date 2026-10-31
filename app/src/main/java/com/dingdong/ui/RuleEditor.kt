package com.dingdong.ui

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import com.dingdong.data.Policy
import com.dingdong.data.PolicyStore

/** Add/edit/delete dialog for one rule, writing straight into the policy. */
object RuleEditor {
  fun show(ctx: Context, rule: Policy.Rule?, onDone: () -> Unit) {
    val pad = (16 * ctx.resources.displayMetrics.density).toInt()
    val form = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad / 2, pad, 0) }

    fun field(label: String, hint: String, value: String, digits: Boolean = false): EditText {
      form.addView(TextView(ctx).apply { text = label; setPadding(0, pad / 2, 0, 0) })
      return EditText(ctx).apply {
        setText(value)
        this.hint = hint
        inputType = if (digits) InputType.TYPE_CLASS_PHONE else InputType.TYPE_CLASS_TEXT
        form.addView(this)
      }
    }

    val enabled = SwitchCompat(ctx).apply { text = "On"; isChecked = rule?.enabled ?: true }
    form.addView(enabled)
    val name = field("Name", "Carrie on Signal", rule?.name.orEmpty())
    form.addView(TextView(ctx).apply {
      text = "Fill in what to match. Every filled-in field must match. Lists are comma-separated; " +
        "case, spaces and punctuation are ignored."
      setPadding(0, pad / 2, 0, 0)
      alpha = 0.7f
    })
    val app = field("App", "signal", rule?.app.orEmpty())
    val from = field("From (sender, any of)", "carrie, caroline", rule?.from.orEmpty().joinToString(", "))
    val any = field("Mentions any of", "urgent, asap", rule?.textAny.orEmpty().joinToString(", "))
    val all = field("Mentions all of", "prod, down", rule?.textAll.orEmpty().joinToString(", "))
    val call = field("Or instead: calls from numbers containing", "47822", rule?.call.orEmpty(), digits = true)

    val builder = AlertDialog.Builder(ctx)
      .setTitle(if (rule == null) "New rule" else "Edit rule")
      .setView(ScrollView(ctx).apply { addView(form) })
      .setPositiveButton("Save", null)
      .setNegativeButton("Cancel", null)
    if (rule != null) builder.setNeutralButton("Delete") { _, _ ->
      PolicyStore.update { p -> p.copy(rules = p.rules.filter { it.name != rule.name }) }
      onDone()
    }
    val dialog = builder.create()
    dialog.setOnShowListener {
      // Validate before closing: the same checks a pasted policy gets.
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        fun list(e: EditText) = e.text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val edited = Policy.Rule(
          name = name.text.toString().trim(),
          enabled = enabled.isChecked,
          app = app.text.toString().trim().ifEmpty { null },
          from = list(from), textAny = list(any), textAll = list(all),
          call = call.text.toString().trim().ifEmpty { null },
        )
        val current = PolicyStore.policy
        val rules = if (rule == null) current.rules + edited
          else current.rules.map { if (it.name == rule.name) edited else it }
        try {
          PolicyStore.save(Policy.parse(current.copy(rules = rules).toJsonString()))
          dialog.dismiss()
          onDone()
        } catch (e: IllegalArgumentException) {
          AlertDialog.Builder(ctx).setMessage(e.message).setPositiveButton("OK", null).show()
        }
      }
    }
    dialog.show()
  }
}
