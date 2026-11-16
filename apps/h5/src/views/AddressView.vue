<template>
  <div class="so-page so-page--flat addr-page">
    <van-nav-bar title="收货地址" left-arrow @click-left="router.back()" />

    <div class="addr-list">
      <p v-if="merchantHint !== ''" class="addr-scope">
        <van-icon name="info-o" size="12" /> {{ merchantHint }}
      </p>

      <button
        v-for="row in address.sorted"
        :key="row.id"
        type="button"
        :class="['addr-card so-card', { 'addr-card--dim': !deliverable(row) }]"
        @click="choose(row)"
      >
        <div class="addr-card__head">
          <span class="addr-card__name">{{ row.contactName }}</span>
          <span class="addr-card__phone so-num">{{ row.contactPhone }}</span>
          <span v-if="row.isDefault" class="addr-card__tag">默认</span>
          <span v-if="row.tag" class="addr-card__tag addr-card__tag--soft">{{ row.tag }}</span>
        </div>
        <div class="addr-card__full">{{ fullAddressOf(row) }}</div>
        <div v-if="row.remark" class="addr-card__remark">{{ row.remark }}</div>
        <div v-if="!deliverable(row)" class="addr-card__warn">该档口不配送此校区</div>
        <div class="addr-card__ops">
          <button type="button" class="addr-op" @click.stop="edit(row)">
            <van-icon name="edit" size="13" /> 编辑
          </button>
          <button v-if="!row.isDefault" type="button" class="addr-op" @click.stop="address.setDefault(row.id)">
            <van-icon name="star-o" size="13" /> 设为默认
          </button>
          <button type="button" class="addr-op addr-op--del" @click.stop="drop(row)">
            <van-icon name="delete-o" size="13" /> 删除
          </button>
          <van-icon v-if="address.pickedId === row.id" name="success" size="16" class="addr-card__pick" />
        </div>
      </button>

      <EmptyState
        v-if="address.list.length === 0"
        icon="location-o"
        title="还没有收货地址"
        description="添加寝室地址后，外卖配送才能选到它。"
        action-text="新增地址"
        @action="create()"
      />

      <p v-else-if="address.isFull" class="addr-full">地址簿已满 {{ MAX_LABEL }} 条，删除后再新增。</p>
    </div>

    <div class="addr-bar">
      <button type="button" class="addr-bar__btn" :disabled="address.isFull" @click="create()">
        <van-icon name="plus" size="16" /> 新增收货地址
      </button>
    </div>

    <van-popup :show="open" position="bottom" round :style="{ maxHeight: '88dvh' }" @update:show="open = $event">
      <div class="form">
        <div class="form__head">
          <span class="form__title">{{ editingId === "" ? "新增地址" : "编辑地址" }}</span>
          <button type="button" class="form__x" aria-label="关闭" @click="open = false">
            <van-icon name="cross" size="17" />
          </button>
        </div>

        <van-field v-model="draft.contactName" label="联系人" placeholder="姓名" maxlength="20" />
        <van-field v-model="draft.contactPhone" label="手机号" type="tel" placeholder="11 位手机号" maxlength="11" />
        <van-field
          v-model="draft.campusArea"
          label="校区"
          readonly
          is-link
          placeholder="选择校区"
          @click="areaOpen = true"
        />
        <van-field v-model="draft.building" label="楼栋" placeholder="如 3 号楼 / 紫荆公寓 A 座" maxlength="30" />
        <van-field v-model="draft.roomNo" label="寝室号" placeholder="如 412" maxlength="12" />
        <van-field label="标签">
          <template #input>
            <div class="form__tags">
              <button
                v-for="tag in TAGS"
                :key="tag"
                type="button"
                :class="['form__tag', { 'form__tag--on': draft.tag === tag }]"
                @click="draft.tag = draft.tag === tag ? '' : tag"
              >
                {{ tag }}
              </button>
            </div>
          </template>
        </van-field>
        <van-field
          v-model="draft.remark"
          label="备注"
          type="textarea"
          rows="1"
          autosize
          maxlength="50"
          placeholder="如：放门口不要按门铃"
        />
        <van-cell title="设为默认地址">
          <template #right-icon>
            <van-switch v-model="draft.isDefault" size="20" />
          </template>
        </van-cell>

        <div v-if="error !== ''" class="form__error">{{ error }}</div>

        <div class="form__foot">
          <button type="button" class="form__ghost" @click="open = false">取消</button>
          <button type="button" class="form__main" :disabled="saving" @click="save">保存</button>
        </div>
      </div>
    </van-popup>

    <van-popup :show="areaOpen" position="bottom" round @update:show="areaOpen = $event">
      <div class="area-sheet">
        <div class="area-sheet__title">选择校区</div>
        <van-picker
          :columns="areaColumns"
          :model-value="draft.campusArea === '' ? [] : [draft.campusArea]"
          confirm-button-text="确定"
          cancel-button-text="取消"
          @confirm="onAreaConfirm"
          @cancel="areaOpen = false"
        />
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { showConfirmDialog } from "vant";
import { catalog, type MenuResponse, type MerchantSummary, type PickupPoint } from "@smartorder/api-client";
import { cachePeek } from "../utils/cache";
import EmptyState from "../components/EmptyState.vue";
import { addressError, fullAddressOf, useAddressStore, type Address, type AddressDraft } from "../stores/address";
import { useCampusStore } from "../stores/campus";
import { useCartStore } from "../stores/cart";
import { toastOk, toastText } from "../utils/report";

const router = useRouter();
const address = useAddressStore();
const campus = useCampusStore();
const cart = useCartStore();

const TAGS: readonly string[] = ["寝室", "教学楼", "图书馆", "办公室"];
const MAX_LABEL = "20";

const open = ref(false);
const areaOpen = ref(false);
const saving = ref(false);
const error = ref("");
const editingId = ref("");
const areaOptions = ref<string[]>([]);

const draft = reactive<AddressDraft>({
  contactName: "",
  contactPhone: "",
  campusArea: "",
  building: "",
  roomNo: "",
  tag: "",
  remark: "",
  isDefault: true,
});

const merchant = computed<MerchantSummary | null>(() => {
  const id = cart.activeGroup?.merchantId ?? "";
  if (id === "") {
    return null;
  }
  return cachePeek<MenuResponse>(`menu:${id}`)?.merchant ?? null;
});

const merchantHint = computed(() =>
  merchant.value ? `当前订单来自 ${merchant.value.name}，仅配送${merchant.value.campusArea}。` : "",
);

const areaColumns = computed(() =>
  areaOptions.value.map((text) => ({ text, value: text })),
);

function deliverable(row: Address): boolean {
  const area = merchant.value?.campusArea ?? "";
  return area === "" || row.campusArea === area;
}

function blank(): void {
  draft.contactName = address.picked?.contactName ?? "";
  draft.contactPhone = address.picked?.contactPhone ?? "";
  draft.campusArea = address.picked?.campusArea ?? campus.area;
  draft.building = "";
  draft.roomNo = "";
  draft.tag = "";
  draft.remark = "";
  draft.isDefault = address.list.length === 0;
  error.value = "";
}

function create(): void {
  editingId.value = "";
  blank();
  open.value = true;
}

function edit(row: Address): void {
  editingId.value = row.id;
  draft.contactName = row.contactName;
  draft.contactPhone = row.contactPhone;
  draft.campusArea = row.campusArea;
  draft.building = row.building;
  draft.roomNo = row.roomNo;
  draft.tag = row.tag;
  draft.remark = row.remark;
  draft.isDefault = row.isDefault;
  error.value = "";
  open.value = true;
}

async function drop(row: Address): Promise<void> {
  try {
    await showConfirmDialog({
      title: "删除这条地址",
      message: `${fullAddressOf(row)} 将从地址簿移除。`,
      confirmButtonText: "删除",
      cancelButtonText: "再想想",
    });
  } catch {
    return;
  }
  address.remove(row.id);
  toastOk("已删除");
}

function onAreaConfirm({ selectedOptions }: { selectedOptions: { text: string; value: string }[] }): void {
  draft.campusArea = selectedOptions[0]?.value ?? "";
  areaOpen.value = false;
}

function save(): void {
  const message = addressError(draft);
  if (message !== "") {
    error.value = message;
    return;
  }
  saving.value = true;
  const next: AddressDraft = {
    contactName: draft.contactName,
    contactPhone: draft.contactPhone,
    campusArea: draft.campusArea,
    building: draft.building,
    roomNo: draft.roomNo,
    tag: draft.tag,
    remark: draft.remark,
    isDefault: draft.isDefault,
  };
  if (editingId.value === "") {
    const created = address.create(next);
    if (created === null) {
      error.value = "地址数量已达上限，请先删除一条";
      saving.value = false;
      return;
    }
    toastOk("地址已保存");
  } else if (!address.update(editingId.value, next)) {
    error.value = "保存失败，请检查填写内容";
    saving.value = false;
    return;
  } else {
    toastOk("地址已更新");
  }
  saving.value = false;
  open.value = false;
}

function choose(row: Address): void {
  if (!deliverable(row)) {
    toastText(`该档口只配送${merchant.value?.campusArea ?? "对应校区"}`);
    return;
  }
  address.pick(row.id);
  router.back();
}

onMounted(async () => {
  const points = await catalog.pickupPoints().catch(() => [] as PickupPoint[]);
  const fromPoints = points.map((point) => point.campusArea);
  const merchants =
    cachePeek<MerchantSummary[]>(`merchants:${campus.area}`) ??
    (fromPoints.length > 0 ? [] : await catalog.merchants(campus.area).catch(() => []));
  areaOptions.value = Array.from(
    new Set([...fromPoints, ...merchants.map((row) => row.campusArea)].filter((text) => text !== "")),
  );
});
</script>

<style scoped>
.addr-page {
  padding-bottom: 5.5rem;
}

.addr-list {
  padding: 0.6rem 0.9rem 0;
}

.addr-scope {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  margin-bottom: 0.6rem;
  color: var(--content-meta);
  font-size: 0.72rem;
}

.addr-card {
  position: relative;
  display: block;
  width: 100%;
  padding: 0.85rem 0.9rem 0.6rem;
  margin-bottom: 0.7rem;
  background: var(--card-bg);
  border: 1px solid transparent;
  text-align: left;
}

.addr-card--dim {
  opacity: 0.62;
}

.addr-card__head {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  flex-wrap: wrap;
}

.addr-card__name {
  color: var(--deep-text);
  font-size: 0.94rem;
  font-weight: var(--font-weight-bold);
}

.addr-card__phone {
  color: var(--content-meta);
  font-size: 0.8rem;
}

.addr-card__tag {
  padding: 0.08rem 0.35rem;
  color: #fff;
  background: var(--primary);
  border-radius: 5px;
  font-size: 0.6rem;
}

.addr-card__tag--soft {
  color: var(--content-meta);
  background: var(--btn-regular-bg);
}

.addr-card__full {
  margin-top: 0.3rem;
  color: var(--deep-text);
  font-size: 0.85rem;
}

.addr-card__remark {
  margin-top: 0.15rem;
  color: var(--content-meta);
  font-size: 0.72rem;
}

.addr-card__warn {
  margin-top: 0.3rem;
  color: var(--caution);
  font-size: 0.72rem;
}

.addr-card__ops {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.6rem;
  padding-top: 0.5rem;
  border-top: 1px solid var(--line-divider);
}

.addr-op {
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
  padding: 0.2rem 0.5rem;
  color: var(--content-meta);
  background: var(--btn-regular-bg);
  border: 0;
  border-radius: 8px;
  font-size: 0.72rem;
}

.addr-op--del {
  color: var(--caution);
}

.addr-card__pick {
  margin-left: auto;
  color: var(--primary);
}

.addr-full {
  padding: 0.2rem 0.2rem 0.8rem;
  color: var(--muted);
  font-size: 0.72rem;
  text-align: center;
}

.addr-bar {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  bottom: 0;
  width: 100%;
  max-width: var(--app-max);
  padding: 0.7rem 0.9rem calc(0.7rem + env(safe-area-inset-bottom));
  background: var(--float-panel-bg);
  border-top: 1px solid var(--line-divider);
  z-index: 20;
}

.addr-bar__btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.3rem;
  width: 100%;
  height: var(--btn-h);
  color: #fff;
  background: linear-gradient(135deg, var(--primary), var(--primary-dark));
  border: 0;
  border-radius: var(--btn-radius);
  font-size: var(--fs-btn);
  font-weight: var(--font-weight-bold);
}

.addr-bar__btn:disabled {
  color: var(--muted);
  background: var(--btn-regular-bg);
}

.form {
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.form__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.9rem 1rem 0.4rem;
}

.form__title {
  color: var(--deep-text);
  font-size: 1rem;
  font-weight: var(--font-weight-bold);
}

.form__x {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  color: var(--muted);
  background: transparent;
  border: 0;
}

.form__tags {
  display: flex;
  gap: 0.4rem;
  flex-wrap: wrap;
}

.form__tag {
  padding: 0.22rem 0.6rem;
  color: var(--content-meta);
  background: var(--btn-regular-bg);
  border: 1px solid transparent;
  border-radius: 999px;
  font-size: 0.74rem;
}

.form__tag--on {
  color: var(--primary-dark);
  background: var(--primary-soft);
  border-color: var(--primary);
}

.form__error {
  padding: 0.5rem 1rem;
  color: var(--caution);
  font-size: 0.76rem;
}

.form__foot {
  display: flex;
  gap: 0.6rem;
  padding: 0.8rem 1rem calc(0.8rem + env(safe-area-inset-bottom));
}

.form__ghost,
.form__main {
  flex: 1;
  height: var(--btn-h);
  border: 0;
  border-radius: var(--btn-radius);
  font-size: var(--fs-btn);
  font-weight: var(--font-weight-semibold);
}

.form__ghost {
  color: var(--content-meta);
  background: var(--btn-regular-bg);
}

.form__main {
  color: #fff;
  background: linear-gradient(135deg, var(--primary), var(--primary-dark));
}

.form__main:disabled {
  opacity: 0.6;
}

.area-sheet {
  padding: 0.9rem 0 0.4rem;
}

.area-sheet__title {
  padding: 0 1rem 0.6rem;
  color: var(--deep-text);
  font-size: 0.98rem;
  font-weight: var(--font-weight-bold);
}
</style>
