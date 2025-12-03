// 文件: index.js (最终稳定版：包含自动设置默认设备和修复 Chart.js 的 Bug)
// ⭐️ 修正点：新增 sendControlCommand 函数，用于统一发送指令
// ⭐️ 修正点：修改 setHumidBtn, heatBtn, checkAICommand 调用 sendControlCommand

const MAX_DATA_POINTS = 60;

let timeLabels = [];
let tempData = [];
let humidityData = [];
let chart;

const API_KEY = '07f1b15756b74cfdb9c135254252511';
const DEFAULT_CITY = '成都';


document.addEventListener('DOMContentLoaded', function() {

    const ctx = document.getElementById('tempHumidityChart');
    if (!ctx) {
        console.error("致命错误：无法找到 ID 为 'tempHumidityChart' 的 Canvas 元素。");
        const container = document.querySelector('.chart-container');
        if (container) {
            container.style.height = '350px';
        }
        return;
    }

    ctx.style.height = '350px';
    ctx.style.width = '100%';

    // 初始设置占位符
    if (timeLabels.length === 0) {
        timeLabels.push('加载中...');
        tempData.push(null);
        humidityData.push(null);
    }

    chart = new Chart(ctx.getContext('2d'), {
        type: 'line',
        data: {
            labels: timeLabels,
            datasets: [
                {
                    label: '温度 (℃)',
                    data: tempData,
                    borderColor: 'rgb(255, 99, 132)',
                    backgroundColor: 'rgba(255, 99, 132, 0.2)',
                    yAxisID: 'yTemp',
                    fill: true,
                    tension: 0.1
                },
                {
                    label: '湿度 (%)',
                    data: humidityData,
                    borderColor: 'rgb(54, 162, 235)',
                    backgroundColor: 'rgba(54, 162, 235, 0.2)',
                    yAxisID: 'yHumid',
                    fill: true,
                    tension: 0.1
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                yTemp: {
                    type: 'linear',
                    display: true,
                    position: 'left',
                    title: { display: true, text: '温度 (℃)' },
                    suggestedMin: 20, suggestedMax: 35
                },
                yHumid: {
                    type: 'linear',
                    display: true,
                    position: 'right',
                    title: { display: true, text: '湿度 (%)' },
                    suggestedMin: 40, suggestedMax: 80,
                    grid: { drawOnChartArea: false }
                }
            },
            plugins: {
                tooltip: {
                    mode: 'index',
                    intersect: false,
                }
            },
            hover: {
                mode: 'nearest',
                intersect: true
            }
        }
    });

    // 启动数据获取循环
    fetchData();
    setInterval(fetchData, 5000);

    // 交互事件处理 (修改为调用 sendControlCommand)
    document.getElementById('setHumidBtn').addEventListener('click', () => {
        const target = document.getElementById('targetHumid').value;
        if (target && !isNaN(target)) {
            sendControlCommand('humid', parseFloat(target));
        } else {
            alert('请输入有效的目标湿度！');
        }
    });

    document.getElementById('heatBtn').addEventListener('click', () => {
        const target = document.getElementById('targetTemp').value;
        if (target && !isNaN(target)) {
            sendControlCommand('heat', parseFloat(target));
        } else {
            alert('请输入有效的目标温度！');
        }
    });

    document.getElementById('getWeatherBtn').addEventListener('click', () => {
        const city = document.getElementById('cityInput').value.trim();
        if (city) fetchWeather(city);
    });

    document.getElementById('cityInput').value = DEFAULT_CITY;
    fetchWeather(DEFAULT_CITY);

    // 启动 AI 助手检查
    checkAICommand();
    // 移除此处对 checkAICommand 的重复调用，防止双重提示
    // setInterval(checkAICommand, 1000);
});

/**
 * ⭐️ 新增函数：发送控制指令到后端
 * @param {string} type - 控制类型 ('heat' 或 'humid')
 * @param {number} value - 目标值
 */
async function sendControlCommand(type, value) {
    if (isNaN(value)) {
        alert('无效的控制值！');
        return;
    }

    try {
        const response = await fetch('/api/control', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                type: type,
                value: value
            }),
            credentials: 'include'
        });

        const data = await response.json();

        if (response.ok) {
            const actionText = type === 'heat' ? '升温指令' : '提高湿度指令';
            const unit = type === 'heat' ? '℃' : '%';
            alert(`✅ ${actionText}已发送！目标设定为 ${value}${unit}。`);
            console.log(`发送指令成功: ${data.message}`);
        } else if (response.status === 401) {
            alert('❌ 未登录或会话过期，请重新登录。');
        } else {
            alert(`❌ 指令发送失败: ${data.message || '未知错误'}`);
            console.error('发送指令失败:', data.message);
        }
    } catch (error) {
        console.error('网络请求错误，无法发送指令:', error);
        alert('❌ 网络请求错误，无法发送指令。');
    }
}


// ⭐️ 修正后的 checkAICommand：调用 sendControlCommand
function checkAICommand() {
    const aiSetTemp = localStorage.getItem("aiSetTemp");
    const aiAutoHeat = localStorage.getItem("aiAutoHeat");
    const aiSetHumid = localStorage.getItem("aiSetHumid");
    const aiAutoHumid = localStorage.getItem("aiAutoHumid");

    let tempExecuted = false;

    if (aiSetTemp && aiAutoHeat === "true" && !isNaN(parseFloat(aiSetTemp))) {
        const targetTemp = parseFloat(aiSetTemp);
        const targetTempInput = document.getElementById('targetTemp');
        targetTempInput.value = targetTemp;

        // 调用新的发送函数
        setTimeout(() => {
            sendControlCommand('heat', targetTemp);
            localStorage.removeItem("aiSetTemp");
            localStorage.removeItem("aiAutoHeat");
            console.log(`AI助手指令(1/2)已执行：目标温度设置为 ${aiSetTemp}℃ 并发送升温指令。`);
        }, 500);
        tempExecuted = true;
    } else {
        localStorage.removeItem("aiSetTemp");
        localStorage.removeItem("aiAutoHeat");
    }

    if (aiSetHumid && aiAutoHumid === "true" && !isNaN(parseFloat(aiSetHumid))) {
        const targetHumid = parseFloat(aiSetHumid);
        const targetHumidInput = document.getElementById('targetHumid');
        targetHumidInput.value = targetHumid;

        const delay = tempExecuted ? 1500 : 500;

        // 调用新的发送函数
        setTimeout(() => {
            sendControlCommand('humid', targetHumid);
            localStorage.removeItem("aiSetHumid");
            localStorage.removeItem("aiAutoHumid");
            console.log(`AI助手指令(${tempExecuted ? '2/2' : '1/1'})已执行：目标湿度设置为 ${aiSetHumid}% 并发送提高湿度指令。`);
        }, delay);
    } else {
        localStorage.removeItem("aiSetHumid");
        localStorage.removeItem("aiAutoHumid");
    }
}


// ... (trySetDefaultDevice, fetchData, fetchWeather, displayWeather 函数保持不变) ...
// 为避免冗长，此处省略了未修改的方法，请将新增方法和修改后的方法加入到原文件中。

// ⭐️ 原始文件中的 trySetDefaultDevice 函数 (未修改)
async function trySetDefaultDevice() {
    try {
        const response = await fetch('/api/setDefaultActiveDevice', {
            method: 'GET',
            credentials: 'include'
        });
        if (response.ok) {
            console.log("✅ 后端已自动设置默认活跃设备，即将重新加载数据。");
            const deviceNameEl = document.getElementById('deviceName');
            if(deviceNameEl) {
                deviceNameEl.textContent = "已设置默认设备，正在加载数据...";
            }
            return true;
        } else {
            const data = await response.json();
            // 如果是因为“用户没有注册任何设备”而失败
            if (response.status === 404) {
                const deviceNameEl = document.getElementById('deviceName');
                if(deviceNameEl) deviceNameEl.textContent = "请先在设备管理页注册设备";
            } else {
                const deviceNameEl = document.getElementById('deviceName');
                if(deviceNameEl) deviceNameEl.textContent = data.message || "请在设备管理页选择一个活跃设备";
            }
            console.warn("⚠️ 自动设置默认设备失败：", data.message);
            return false;
        }
    } catch (error) {
        console.error("❌ 尝试设置默认设备时发生网络错误:", error);
        return false;
    }
}


// ⭐️ 原始文件中的 fetchData 函数 (未修改)
async function fetchData() {
    let shouldUpdateChart = false;

    try {
        const response = await fetch('/api/my-device-data', {
            method: 'GET',
            credentials: 'include'
        });

        let devicesData = [];
        let isErrorOrEmpty = false;
        let isUnauthorized = false;

        if (response.status === 401) {
            console.error("❌ 错误 401: 未登录或会话过期，请重新登录。");
            isErrorOrEmpty = true;
            isUnauthorized = true;
        } else if (!response.ok) {
            console.error(`❌ HTTP 错误! 状态码: ${response.status}`);
            throw new Error(`HTTP 错误! 状态码: ${response.status}`);
        } else {
            devicesData = await response.json();
        }

        // --- 错误或空数据处理 ---
        if (devicesData.length === 0 || isErrorOrEmpty) {

            const deviceNameEl = document.getElementById('deviceName');

            if (isUnauthorized) {
                if(deviceNameEl) deviceNameEl.textContent = "未登录或会话过期";
            } else if (!isErrorOrEmpty && devicesData.length === 0) {
                // ⭐️ 核心修正：如果用户已登录但没有活跃设备，则尝试设置默认设备
                const success = await trySetDefaultDevice();
                if (success) {
                    return fetchData(); // 尝试重新获取数据
                }
                // 如果 trySetDefaultDevice 失败，它已经设置了设备名称的错误信息
            } else if (devicesData.length === 0) {
                if(deviceNameEl) deviceNameEl.textContent = "请在设备管理页选择一个活跃设备";
            }


            // 只有当数组为空时，才设置占位符
            if (timeLabels.length === 0) {
                timeLabels.push('无数据');
                tempData.push(null);
                humidityData.push(null);
                shouldUpdateChart = true;
            }

            // 如果只有占位符，强制更新一次图表，否则不更新（避免闪烁）
            if (timeLabels.length === 1 && timeLabels[0] === '无数据' && chart) {
                chart.update('none');
            }
            return;
        }

        // --- 有数据时的处理 ---
        const latestData = devicesData[0];

        const newTemp = Number(latestData.temperature);
        const newHumid = Number(latestData.humidity);
        const newLight = Number(latestData.light);
        const deviceName = latestData.deviceName || "未知设备";

        // 使用设备时间戳（API 返回的 timestamp）
        const date = new Date(latestData.timestamp);
        const timeString = `${date.getHours().toString().padStart(2,'0')}:${date.getMinutes().toString().padStart(2,'0')}:${date.getSeconds().toString().padStart(2,'0')}`;

        // 检查是否与数组中的最后一个点重复
        const lastIndex = timeLabels.length - 1;

        if (lastIndex >= 0 && timeLabels[lastIndex] === timeString &&
            tempData[lastIndex] === newTemp && humidityData[lastIndex] === newHumid) {

            console.log("⚠️ 检测到重复数据，跳过图表更新。");

            // 但仍需更新 DOM 文本显示
            const currentTempEl = document.getElementById('currentTemp');
            const currentHumidEl = document.getElementById('currentHumid');
            const currentLightEl = document.getElementById('currentLight');
            const deviceNameEl = document.getElementById('deviceName');

            if (currentTempEl && !isNaN(newTemp)) currentTempEl.textContent = newTemp.toFixed(1);
            if (currentHumidEl && !isNaN(newHumid)) currentHumidEl.textContent = newHumid.toFixed(0);
            if (currentLightEl && !isNaN(newLight)) currentLightEl.textContent = newLight.toFixed(1);
            if (deviceNameEl) deviceNameEl.textContent = deviceName;

            return; // 结束函数，不进行图表数组操作和 update
        }


        // 移除占位符
        if (timeLabels.length === 1 && /加载中|无数据/.test(String(timeLabels[0]))) {
            timeLabels.length = 0;
            tempData.length = 0;
            humidityData.length = 0;
        }

        // 推入真实数据
        timeLabels.push(timeString);
        tempData.push(Number.isFinite(newTemp) ? newTemp : null);
        humidityData.push(Number.isFinite(newHumid) ? newHumid : null);

        // 保持最大点数
        while (timeLabels.length > MAX_DATA_POINTS) {
            timeLabels.shift();
            tempData.shift();
            humidityData.shift();
        }

        // 更新 DOM 显示
        const currentTempEl = document.getElementById('currentTemp');
        const currentHumidEl = document.getElementById('currentHumid');
        const currentLightEl = document.getElementById('currentLight');
        const deviceNameEl = document.getElementById('deviceName');

        if (currentTempEl && !isNaN(newTemp)) currentTempEl.textContent = newTemp.toFixed(1);
        if (currentHumidEl && !isNaN(newHumid)) currentHumidEl.textContent = newHumid.toFixed(0);
        if (currentLightEl && !isNaN(newLight)) currentLightEl.textContent = newLight.toFixed(1);
        if (deviceNameEl) deviceNameEl.textContent = deviceName;

        shouldUpdateChart = true;

    } catch (error) {
        console.error("❌ 获取传感器数据失败或解析错误:", error);
    } finally {
        if (chart && shouldUpdateChart) {
            chart.update('none');
            console.log("✅ Chart.js 已强制更新");
        }
    }
}
// --- fetchWeather / displayWeather (保持不变) ---

async function fetchWeather(city) {
    const weatherInfoDiv = document.getElementById('weatherInfo');
    if (!API_KEY || API_KEY === "YOUR_API_KEY") {
        weatherInfoDiv.innerHTML = '<p style="color:red;">⚠️ 请先填写 WeatherAPI Key！</p>';
        return;
    }
    const apiUrl = `https://api.weatherapi.com/v1/current.json?key=${API_KEY}&q=${city}&lang=zh`;
    try {
        weatherInfoDiv.innerHTML = `<p>正在查询 ${city} 的天气...</p>`;
        const response = await fetch(apiUrl);
        if (!response.ok) { throw new Error(`无法获取 ${city} 的天气，请检查城市名称`); }
        const data = await response.json();
        displayWeather(data);
    } catch (error) {
        weatherInfoDiv.innerHTML = `<p style="color:red;">错误：${error.message}</p>`;
        console.error("WeatherAPI 获取失败：", error);
    }
}

function displayWeather(data) {
    const weatherInfoDiv = document.getElementById('weatherInfo');
    const temp = data.current.temp_c;
    const description = data.current.condition.text;
    const iconUrl = "https:" + data.current.condition.icon;
    weatherInfoDiv.innerHTML = `
        <p>
            <img src="${iconUrl}" class="weather-icon">
            <strong>${data.location.name}</strong>（实时）
        </p>
        <p>🌡️ 温度：<strong>${temp} ℃</strong></p>
        <p>☁️ 描述：${description}</p>
        <p>💧 湿度：${data.current.humidity} %</p>
        <p>💨 风速：${data.current.wind_kph} km/h</p>
    `;
}